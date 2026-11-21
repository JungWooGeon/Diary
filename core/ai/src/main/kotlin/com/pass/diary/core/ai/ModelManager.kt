package com.pass.diary.core.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** One entry of shared/ai/llm/models.json (URL/sha256 may be overridden by Remote Config: ARCHITECTURE §5.4). */
data class ModelSpec(
    val id: String, val name: String, val url: String, val fileName: String,
    val sha256: String?, val sizeBytes: Long, val minRamGb: Int, val premium: Boolean,
) {
    companion object {
        fun parse(models: JsonArray) = models.map { it.obj }.map {
            ModelSpec(it.str("id")!!, it.str("name") ?: it.str("id")!!, it.str("url")!!, it.str("fileName")!!,
                it.str("sha256"), it["sizeBytes"]!!.jsonPrimitive.long,
                it.int("minRamGb")!!, it.bool("premium") ?: false)
        }
    }
}

/** What the gate needs from the phone. Android impl below; faked in tests. */
interface DeviceInfo {
    val totalRamBytes: Long
    fun freeBytes(dir: File): Long
    val onUnmeteredNetwork: Boolean
}

class AndroidDeviceInfo(private val context: Context) : DeviceInfo {
    override val totalRamBytes: Long
        get() = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }.totalMem
    override fun freeBytes(dir: File): Long = StatFs(dir.apply { mkdirs() }.path).availableBytes
    override val onUnmeteredNetwork: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        }
}

sealed interface DownloadState {
    data class Progress(val bytes: Long, val total: Long) : DownloadState
    data object Verifying : DownloadState
    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * "온이 깨우기" model files (ARCHITECTURE §5.3): device gate, resumable download (HTTP Range) into [dir],
 * sha256 check, delete. The only network use in core:ai. Keep the app usable while downloading: collect
 * [download] from a WorkManager worker / app scope, not a screen.
 */
class ModelManager(
    val specs: List<ModelSpec>,
    private val dir: File,
    private val device: DeviceInfo,
) {
    enum class Gate { OK, LOW_RAM, LOW_STORAGE }

    /** Menu visibility: models this phone can run at all (RAM only; storage is checked before download). */
    fun supported(): List<ModelSpec> = specs.filter { ramOk(it) }

    fun gate(spec: ModelSpec): Gate = when {
        !ramOk(spec) -> Gate.LOW_RAM
        device.freeBytes(dir) < spec.sizeBytes - partFile(spec).length() + STORAGE_MARGIN -> Gate.LOW_STORAGE
        else -> Gate.OK
    }

    /** Show "Wi-Fi 권장" before starting when this is false. */
    val wifiRecommended: Boolean get() = !device.onUnmeteredNetwork

    fun file(spec: ModelSpec) = File(dir, spec.fileName)
    fun isInstalled(spec: ModelSpec) = file(spec).let { it.exists() && it.length() == spec.sizeBytes }
    /** Best installed model: premium first if the phone can run it. */
    fun installed(): ModelSpec? = specs.filter { isInstalled(it) && ramOk(it) }.maxByOrNull { if (it.premium) 1 else 0 }

    /**
     * Also cancels: bumps [spec]'s generation, so a download already past its last byte (verifying, about to rename)
     * deletes its file instead of installing it. Cancel always wins.
     */
    fun delete(spec: ModelSpec) = synchronized(this) {
        generations[spec.id] = generation(spec) + 1
        file(spec).delete(); partFile(spec).delete()
        Unit
    }
    private val generations = HashMap<String, Int>()
    private fun generation(spec: ModelSpec) = synchronized(this) { generations[spec.id] ?: 0 }

    /** Test seam: the sha256 step (seconds on 2.6 GB). */
    internal var verify: (File) -> String = ::sha256
    /** Bytes of an unfinished download (paused / interrupted): resumable by [download]. */
    fun partialBytes(spec: ModelSpec): Long = partFile(spec).length()

    fun download(spec: ModelSpec): Flow<DownloadState> = flow {
        val gen = generation(spec)
        if (isInstalled(spec)) { emit(DownloadState.Done(file(spec))); return@flow }
        gate(spec).let { if (it != Gate.OK) { emit(DownloadState.Failed(it.name)); return@flow } }
        dir.mkdirs()
        val part = partFile(spec)
        try {
            var have = part.length()
            if (have < spec.sizeBytes) {
                val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true // HF -> CDN redirect
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    when (conn.responseCode) {
                        HttpURLConnection.HTTP_PARTIAL -> Unit
                        HttpURLConnection.HTTP_OK -> have = 0 // server ignored Range: start over
                        416 -> have = part.length() // already complete
                        else -> throw IOException("HTTP ${conn.responseCode}")
                    }
                    if (conn.responseCode != 416) conn.inputStream.use { input ->
                        FileOutputStream(part, have > 0).use { out ->
                            val buf = ByteArray(1 shl 16); var lastEmit = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); have += n
                                if (have - lastEmit >= PROGRESS_STEP || have == spec.sizeBytes) {
                                    emit(DownloadState.Progress(have, spec.sizeBytes)); lastEmit = have
                                }
                            }
                        }
                    }
                } finally { conn.disconnect() }
            }
            emit(DownloadState.Verifying)
            if (part.length() != spec.sizeBytes || (spec.sha256 != null && !verify(part).equals(spec.sha256, true))) {
                part.delete() // corrupt or the HF file changed: never resume onto it
                emit(DownloadState.Failed("CHECKSUM")); return@flow
            }
            // Cancel wins: after a delete() since we started (or with this worker stopped) the file is never installed.
            // delete() already removed .part; a merely stopped worker (pause) keeps it to resume.
            val stopped = !currentCoroutineContext().isActive
            val installed = !stopped && synchronized(this) { generation(spec) == gen && part.renameTo(file(spec)) }
            if (!installed) {
                if (stopped || generation(spec) != gen) { emit(DownloadState.Failed("CANCELLED")); return@flow }
                throw IOException("rename failed")
            }
            emit(DownloadState.Done(file(spec)))
        } catch (e: IOException) {
            emit(DownloadState.Failed("NETWORK: ${e.message}")) // .part kept -> next call resumes
        }
    }.flowOn(Dispatchers.IO)

    private fun ramOk(spec: ModelSpec) = device.totalRamBytes >= spec.minRamGb * RAM_GB_FACTOR
    private fun partFile(spec: ModelSpec) = File(dir, spec.fileName + ".part")

    companion object {
        /** totalMem of an "8GB" phone is ~7.3–7.7 GiB after the kernel's reservation; calibration knob. */
        const val RAM_GB_FACTOR = 0.9e9
        const val STORAGE_MARGIN = 500L * 1024 * 1024
        private const val PROGRESS_STEP = 4L * 1024 * 1024

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s -> val b = ByteArray(1 shl 20); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
