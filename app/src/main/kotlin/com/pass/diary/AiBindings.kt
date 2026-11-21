package com.pass.diary

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pass.diary.core.ai.AiEvent
import com.pass.diary.core.ai.AiPipeline
import com.pass.diary.core.ai.CrisisDetector
import com.pass.diary.core.ai.DownloadState
import com.pass.diary.core.ai.EmotionClassifier
import com.pass.diary.core.ai.ModelManager
import com.pass.diary.core.ai.ModelSpec
import com.pass.diary.core.ai.OniLlm
import com.pass.diary.core.ai.TagExtractor
import com.pass.diary.core.ai.TemplateReplyEngine
import com.pass.diary.core.billing.PremiumState
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Tag
import com.pass.diary.feature.my.ModelCard
import com.pass.diary.feature.my.ModelState
import com.pass.diary.feature.my.ModelStateProvider
import com.pass.diary.feature.write.ChatResponder
import com.pass.diary.feature.write.ChatTurn
import com.pass.diary.feature.write.ReplyProvider
import com.pass.diary.feature.write.ReplyResult
import com.pass.diary.feature.write.WritingAssistant
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.pass.diary.core.ai.ChatResponder as CoreChat

/*
 * core:ai behind the feature seams (feature/write AiHooks.kt, feature/my ModelState.kt). core:ai objects are taken as
 * dagger.Lazy: building them reads the 14 MB classifier, which must not happen on the main thread when a screen opens.
 */

// ---------------------------------------------------------------------------------------------------- 답장 (§5.1)

class AiReplyProvider @Inject constructor(
    private val oni: Lazy<OniLlm>,
    private val crisis: Lazy<CrisisDetector>,
    private val classifier: Lazy<EmotionClassifier>,
    private val tags: Lazy<TagExtractor>,
    private val template: Lazy<TemplateReplyEngine>,
    private val settings: SettingsRepository,
) : ReplyProvider {
    override fun reply(entry: Entry, recentTemplateIds: Set<String>): Flow<ReplyResult> = flow {
        // ① before anything heavy loads (the classifier takes seconds on a cold start): a crisis card must never wait.
        // It also runs with every AI switch off: it is a safety rule (설정 › AI says so).
        if (crisis.get().detect(entry.aiText()).crisis) { emit(ReplyResult.Crisis); return@flow }
        val s = settings.settings.first()
        if (!s.aiReply) { emit(ReplyResult.Off); return@flow } // no classifier, no LLM
        fun pipeline(llm: Boolean) = AiPipeline(crisis.get(), classifier.get(), tags.get(), template.get()) { if (llm && s.aiChat) oni.get().engine() else null }
            .onSave(entry.aiText(), entry.emotions, entry.tags, recentTemplateIds)
        val t0 = System.currentTimeMillis()
        val tLoaded = classifier.get().let { System.currentTimeMillis() } // cold start: ORT session + tokenizer
        replyWithFallback(pipeline(llm = true), { pipeline(llm = false) }, REPLY_TIMEOUT_MS).collect {
            if (it is ReplyResult.Letter) Log.i("AiReply", "${it.source} reply in ${System.currentTimeMillis() - t0} ms (AI load ${tLoaded - t0} ms)")
            emit(it)
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun isCrisis(entry: Entry) = withContext(Dispatchers.Default) { crisis.get().detect(entry.aiText()).crisis }

    companion object {
        /** First reply after install also loads the model (~10 s on a phone); then 3–9 s (REPORT). Past this: library reply. */
        const val REPLY_TIMEOUT_MS = 60_000L
    }
}

/** What the AI reads: the title only when the user wrote one, then the body. */
internal fun Entry.aiText() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")

internal fun AiEvent.toUi(): ReplyResult? = when (this) {
    is AiEvent.Crisis -> ReplyResult.Crisis
    is AiEvent.Analysis -> null // the editor already offered these as chips; the reply screen doesn't re-ask
    is AiEvent.Reply -> ReplyResult.Letter(text, source, templateId)
}

/**
 * [primary] (may use the LLM: generate → filter → retry once, all off screen) until it finishes or [timeoutMs]; if it
 * ends without a letter (timeout, native error), the library reply from [fallback]. Crisis from either side ends it.
 */
internal fun replyWithFallback(primary: Flow<AiEvent>, fallback: () -> Flow<AiEvent>, timeoutMs: Long): Flow<ReplyResult> = flow {
    var done = false
    withTimeoutOrNull(timeoutMs) {
        // class only, never the message: it may quote diary / LLM text
        primary.catch { Log.w("AiReply", "reply failed, using library: ${it.javaClass.simpleName}") }.collect { e ->
            e.toUi()?.let { emit(it); done = true }
        }
    }
    if (!done) fallback().collect { e -> e.toUi()?.let { emit(it) } }
}

// ---------------------------------------------------------------------------------------------------- 편집기 제안

class AiWritingAssistant @Inject constructor(
    private val classifier: Lazy<EmotionClassifier>,
    private val tags: Lazy<TagExtractor>,
    private val oni: Lazy<OniLlm>,
    private val settings: SettingsRepository,
) : WritingAssistant {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // While typing: classifier only. The LLM emotion JSON costs seconds and holds the one LLM the reply needs next.
    override suspend fun suggestEmotions(body: String): List<EntryEmotion> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiSuggest) emptyList() else classifier.get().classify(body).filter { it.key != EmotionKey.UNSURE }
    }

    override suspend fun suggestTags(body: String): List<Tag> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiSuggest) emptyList() else tags.get().extract(body, max = 5)
    }

    override suspend fun tidy(transcript: String): String =
        withContext(Dispatchers.Default) { oni.get().engine()?.tidy(transcript) ?: transcript }

    override suspend fun refineTitle(body: String): String? = withContext(Dispatchers.Default) { oni.get().engine()?.title(body) }

    override fun warmUp() {
        scope.launch {
            val s = settings.settings.first()
            if (!s.aiSuggest && !s.aiReply) return@launch
            classifier.get() // ORT session + tokenizer: seconds on a cold start, so not when the reply screen opens
            // tidy / title / chat also use the LLM, but only the reply is worth holding ~3 GB for while typing
            if (s.aiReply) runCatching { oni.get().engine()?.warmUp() }.onFailure { Log.w("AiWrite", "LLM warm-up failed: ${it.javaClass.simpleName}") }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- 대화로 쓰기

class AiChatResponder @Inject constructor(private val chat: Lazy<CoreChat>, private val oni: Lazy<OniLlm>) : ChatResponder {
    override val maxQuestions = com.pass.diary.core.ai.OniChat.MAX_QUESTIONS
    override val llmReady: Boolean get() = oni.get().let { it.enabled && it.state().installed != null }

    override suspend fun isCrisis(text: String) = withContext(Dispatchers.Default) { chat.get().isCrisis(text) }

    override suspend fun next(history: List<ChatTurn>): String? = withContext(Dispatchers.Default) {
        if (history.count { it.fromOni } >= maxQuestions) null else chat.get().next(history.toPairs(), time = timeOfDay())
    }

    override fun suggestions(history: List<ChatTurn>) = listOf("다음 질문")

    override suspend fun compose(history: List<ChatTurn>): String = withContext(Dispatchers.Default) { chat.get().compose(history.toPairs()) }

    private fun timeOfDay() = LocalTime.now().hour.let { if (it in 5..10) "morning" else if (it in 11..17) "day" else "night" }
}

/** UI turns -> core (온이 question, user answer) pairs. A skipped question (no answer) is dropped. */
internal fun List<ChatTurn>.toPairs(): List<Pair<String, String>> = buildList {
    var q: String? = null
    for (t in this@toPairs) if (t.fromOni) q = t.text else { add((q ?: "") to t.text); q = null }
}

// ---------------------------------------------------------------------------------------------------- 온이 깨우기

@Singleton
class AiModelStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val oni: Lazy<OniLlm>,
    private val manager: Lazy<ModelManager>,
    private val premium: PremiumState,
) : ModelStateProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("oni_model", Context.MODE_PRIVATE)
    private val work = WorkManager.getInstance(context)
    private val localFailure = MutableStateFlow<Map<String, String>>(emptyMap()) // refused before any download started
    private val bump = MutableStateFlow(0) // file changes WorkManager doesn't see (delete)

    override val deviceRamGb: Int = run {
        val mi = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        Math.round(mi.totalMem / 1e9).toInt() // marketing GB: an "8GB" phone reports ~7.8e9
    }
    override val wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI, true))

    // One unique work per model: its WorkInfo is the current download (finished ones are replaced on the next enqueue).
    override val models: StateFlow<List<ModelCard>> =
        combine(manager.get().specs.map { work.getWorkInfosForUniqueWorkFlow(workName(it.id)) }) { it.map { l -> l.lastOrNull() } }
            .combine(localFailure) { infos, fails -> infos to fails }
            .combine(bump) { (infos, fails), _ -> cards(infos, fails) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** [infos] = latest WorkInfo per spec, same order as [ModelManager.specs]. */
    private fun cards(infos: List<WorkInfo?>, fails: Map<String, String>): List<ModelCard> = manager.get().let { m ->
        val supported = m.supported()
        m.specs.mapIndexed { i, spec ->
            val info = infos[i]
            val state = when {
                spec !in supported -> ModelState.Unsupported(deviceRamGb)
                m.isInstalled(spec) -> ModelState.Ready
                info != null && !info.state.isFinished -> ModelState.Downloading(
                    info.progress.getLong(P_BYTES, m.partialBytes(spec)), info.progress.getLong(P_ETA, -1).takeIf { it >= 0 }, paused = false,
                )
                fails[spec.id] != null -> ModelState.Failed(fails.getValue(spec.id))
                info?.state == WorkInfo.State.FAILED -> ModelState.Failed(failureText(info.outputData.getString(P_REASON).orEmpty(), spec))
                m.partialBytes(spec) > 0 -> ModelState.Downloading(m.partialBytes(spec), null, paused = true)
                else -> ModelState.NotDownloaded
            }
            ModelCard(spec.id, if (spec.premium) "고급 온이" else "기본 온이", spec.name, spec.sizeBytes, spec.minRamGb, spec.premium, state)
        }
    }

    private fun spec(id: String) = manager.get().specs.first { it.id == id }

    override fun download(id: String) {
        scope.launch {
            val spec = spec(id)
            localFailure.update { it - id }
            val refused = when {
                spec.premium && !premium.isPremium.first() -> "프리미엄에서 깨울 수 있어요."
                manager.get().gate(spec) == ModelManager.Gate.LOW_RAM -> failureText("LOW_RAM", spec)
                manager.get().gate(spec) == ModelManager.Gate.LOW_STORAGE -> failureText("LOW_STORAGE", spec)
                else -> null
            }
            if (refused != null) localFailure.update { it + (id to refused) } else enqueue(spec, ExistingWorkPolicy.KEEP)
        }
    }

    private fun enqueue(spec: ModelSpec, policy: ExistingWorkPolicy) {
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(P_ID to spec.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()
        work.enqueueUniqueWork(workName(spec.id), policy, req)
    }

    /** Pause = stop the worker; the .part file stays and the next [download] resumes with an HTTP Range request. */
    override fun pause(id: String) { work.cancelUniqueWork(workName(id)) }

    override fun cancel(id: String) {
        work.cancelUniqueWork(workName(id))
        scope.launch { manager.get().delete(spec(id)); localFailure.update { it - id }; bump.update { it + 1 } }
    }

    override fun delete(id: String) {
        work.cancelUniqueWork(workName(id))
        scope.launch { oni.get().delete(spec(id)); bump.update { it + 1 } } // releases the loaded engine first
    }

    override fun setWifiOnly(on: Boolean) {
        wifiOnly.value = on
        prefs.edit().putBoolean(KEY_WIFI, on).apply()
        // A download waiting for (or running on) the old network rule restarts under the new one; it resumes from .part.
        models.value.filter { it.state is ModelState.Downloading && !(it.state as ModelState.Downloading).paused }
            .forEach { enqueue(spec(it.id), ExistingWorkPolicy.REPLACE) }
    }

    companion object {
        const val TAG = "oni-model"
        const val P_ID = "id"
        const val P_BYTES = "bytes"
        const val P_ETA = "eta"
        const val P_REASON = "reason"
        private const val KEY_WIFI = "wifiOnly"
        fun workName(id: String) = "oni-model-$id"

        internal fun failureText(reason: String, spec: ModelSpec): String = when {
            reason == "LOW_RAM" -> "이 폰에서는 쓸 수 없어요. RAM ${spec.minRamGb}GB 이상이 필요해요."
            reason == "LOW_STORAGE" -> "저장 공간이 부족해요. 약 %.1fGB를 비워 주세요.".format((spec.sizeBytes + ModelManager.STORAGE_MARGIN) / 1e9)
            reason == "CHECKSUM" -> "받은 파일이 손상됐어요. 처음부터 다시 받아 주세요."
            reason.startsWith("NETWORK") -> "인터넷 연결이 끊겼어요. 받은 곳부터 이어서 받을 수 있어요."
            else -> "받지 못했어요. 잠시 뒤에 다시 해 주세요."
        }
    }
}

/**
 * The ~2.6 GB download as a foreground (dataSync) worker so it keeps going with the app in the background.
 * Network drop -> retry with backoff (resumes from .part); checksum / storage -> failure with a reason.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val manager: ModelManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val spec = manager.specs.firstOrNull { it.id == inputData.getString(AiModelStateProvider.P_ID) } ?: return Result.failure()
        // Android 12+ refuses a foreground service started from the background (e.g. a retry firing while the app is
        // closed): try again later instead of crashing the worker.
        if (!foregroundOrRetry { setForeground(info(spec, manager.partialBytes(spec), verifying = false)) }) return Result.retry()
        var result: Result = Result.retry()
        var lastUi = 0L
        var mark = System.currentTimeMillis() to manager.partialBytes(spec)
        var bytesPerSec = 0.0
        manager.download(spec).collect { s ->
            when (s) {
                is DownloadState.Progress -> {
                    val now = System.currentTimeMillis()
                    if (now - lastUi >= 1_000) {
                        val (t0, b0) = mark
                        if (now - t0 >= 3_000) { // smoothed speed over ≥3 s windows
                            val v = (s.bytes - b0) * 1000.0 / (now - t0)
                            bytesPerSec = if (bytesPerSec == 0.0) v else 0.7 * bytesPerSec + 0.3 * v
                            mark = now to s.bytes
                        }
                        val eta = if (bytesPerSec > 0) ((s.total - s.bytes) / bytesPerSec).toLong() else -1L
                        setProgress(workDataOf(AiModelStateProvider.P_BYTES to s.bytes, AiModelStateProvider.P_ETA to eta))
                        foregroundOrRetry { setForeground(info(spec, s.bytes, verifying = false)) }
                        lastUi = now
                    }
                }
                DownloadState.Verifying -> foregroundOrRetry { setForeground(info(spec, spec.sizeBytes, verifying = true)) }
                is DownloadState.Done -> result = Result.success()
                is DownloadState.Failed -> result =
                    if (s.reason.startsWith("NETWORK") && runAttemptCount < MAX_RETRIES) Result.retry()
                    else Result.failure(workDataOf(AiModelStateProvider.P_REASON to s.reason))
            }
        }
        return result
    }

    private fun info(spec: ModelSpec, bytes: Long, verifying: Boolean): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "온이 깨우기", NotificationManager.IMPORTANCE_LOW))
        val pct = (bytes * 100 / spec.sizeBytes).toInt().coerceIn(0, 100)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_oni)
            .setContentTitle(if (verifying) "온이를 깨우는 중 · 파일 확인" else "온이를 깨우는 중 · $pct%")
            .setContentText("%.1fGB / %.1fGB".format(bytes / 1e9, spec.sizeBytes / 1e9))
            .setProgress(100, pct, verifying)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    private companion object {
        const val CHANNEL = "oni_model"
        const val NOTIFICATION_ID = 4201
        const val MAX_RETRIES = 8
    }
}

/** false = the system refused to start the foreground service now (ForegroundServiceStartNotAllowedException, API 31+). */
internal suspend fun foregroundOrRetry(set: suspend () -> Unit): Boolean = try {
    set(); true
} catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException is one; the class only exists on 31+
    Log.w("ModelDownload", "foreground not allowed now: ${e.javaClass.simpleName}")
    false
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingModule {
    @Binds abstract fun reply(impl: AiReplyProvider): ReplyProvider
    @Binds abstract fun chat(impl: AiChatResponder): ChatResponder
    @Binds abstract fun assistant(impl: AiWritingAssistant): WritingAssistant
    @Binds abstract fun models(impl: AiModelStateProvider): ModelStateProvider
}
