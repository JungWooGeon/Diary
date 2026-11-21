package com.pass.diary.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    val md5Checksum: String? = null,
    val modifiedTime: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
) {
    val entityId: String? get() = appProperties[PROP_ENTITY]
    val kind: String? get() = appProperties[PROP_KIND]

    companion object {
        const val PROP_ENTITY = "entityId"
        const val PROP_KIND = "kind"
    }
}

@Serializable data class FilePage(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)
@Serializable data class Change(val fileId: String? = null, val removed: Boolean = false, val file: DriveFile? = null)
@Serializable data class ChangePage(val changes: List<Change> = emptyList(), val nextPageToken: String? = null, val newStartPageToken: String? = null)

@Serializable private class StartToken(val startPageToken: String)

class DriveHttpException(val code: Int, message: String) : IOException("Drive HTTP $code: $message")

/** The slice of Drive REST v3 that sync needs; everything lives in `appDataFolder`. Faked in tests. */
interface DriveApi {
    suspend fun findByEntityId(entityId: String): DriveFile?
    suspend fun listAll(pageToken: String?): FilePage
    suspend fun create(name: String, props: Map<String, String>, mime: String, bytes: ByteArray): DriveFile
    /** Replaces content and renames (an entry's path changes with its date). 404 -> [DriveHttpException]. */
    suspend fun update(fileId: String, name: String, mime: String, bytes: ByteArray): DriveFile
    suspend fun metadata(fileId: String): DriveFile
    suspend fun download(fileId: String): ByteArray
    suspend fun startPageToken(): String
    suspend fun changes(pageToken: String): ChangePage
}

interface AccessTokenProvider {
    /** Throws [SyncAuthException] when the user must sign in / consent in the UI. */
    suspend fun token(): String
    suspend fun invalidate(token: String)
}

class SyncAuthException(message: String, val resolution: android.app.PendingIntent? = null) : Exception(message)

class OkHttpDriveApi @Inject constructor(private val auth: AccessTokenProvider) : DriveApi {
    private val http = OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://www.googleapis.com/drive/v3".toHttpUrl()
    private val upload = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl()

    override suspend fun findByEntityId(entityId: String): DriveFile? {
        val q = "appProperties has { key='${DriveFile.PROP_ENTITY}' and value='${entityId.replace("'", "\\'")}' } and trashed=false"
        return get<FilePage>(files().addQueryParameter("q", q).build()).files.firstOrNull()
    }

    override suspend fun listAll(pageToken: String?): FilePage =
        get(files().addQueryParameter("pageSize", "1000").apply { pageToken?.let { addQueryParameter("pageToken", it) } }
            .addQueryParameter("q", "trashed=false").build())

    override suspend fun create(name: String, props: Map<String, String>, mime: String, bytes: ByteArray): DriveFile {
        val meta = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add("appDataFolder") }
            putJsonObject("appProperties") { props.forEach { (k, v) -> put(k, v) } }
        }.toString()
        return send(Request.Builder().url(upload.newBuilder().q("uploadType", "multipart").q("fields", FIELDS).build())
            .post(multipart(meta, mime, bytes)))
    }

    override suspend fun update(fileId: String, name: String, mime: String, bytes: ByteArray): DriveFile {
        val meta = buildJsonObject { put("name", name) }.toString()
        return send(Request.Builder().url(upload.newBuilder().addPathSegment(fileId).q("uploadType", "multipart").q("fields", FIELDS).build())
            .patch(multipart(meta, mime, bytes)))
    }

    override suspend fun metadata(fileId: String): DriveFile =
        get(base.newBuilder().addPathSegment("files").addPathSegment(fileId).q("fields", FIELDS).build())

    override suspend fun download(fileId: String): ByteArray =
        call(Request.Builder().url(base.newBuilder().addPathSegment("files").addPathSegment(fileId).q("alt", "media").build()))

    override suspend fun startPageToken(): String =
        get<StartToken>(base.newBuilder().addPathSegments("changes/startPageToken").build()).startPageToken

    override suspend fun changes(pageToken: String): ChangePage =
        get(base.newBuilder().addPathSegment("changes").q("pageToken", pageToken).q("spaces", "appDataFolder")
            .q("pageSize", "1000").q("fields", "nextPageToken,newStartPageToken,changes(fileId,removed,file($FIELDS))").build())

    private fun files() = base.newBuilder().addPathSegment("files").q("spaces", "appDataFolder").q("fields", "nextPageToken,files($FIELDS)")

    private fun HttpUrl.Builder.q(k: String, v: String) = addQueryParameter(k, v)

    private fun multipart(meta: String, mime: String, bytes: ByteArray): RequestBody =
        MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody(mime.toMediaType()))
            .build()

    private suspend inline fun <reified T> get(url: HttpUrl): T = json.decodeFromString(call(Request.Builder().url(url)).decodeToString())
    private suspend fun send(b: Request.Builder): DriveFile = json.decodeFromString(call(b).decodeToString())

    /** One retry after invalidating the token on 401 (expired/revoked cached token). */
    private suspend fun call(b: Request.Builder): ByteArray = withContext(Dispatchers.IO) {
        var token = auth.token()
        repeat(2) { attempt ->
            http.newCall(b.header("Authorization", "Bearer $token").build()).execute().use { r ->
                if (r.isSuccessful) return@withContext r.body.bytes()
                if (r.code == 401 && attempt == 0) {
                    auth.invalidate(token); token = auth.token(); return@use
                }
                throw DriveHttpException(r.code, r.body.string().take(300))
            }
        }
        error("unreachable")
    }

    private companion object {
        const val FIELDS = "id,name,md5Checksum,modifiedTime,appProperties"
    }
}
