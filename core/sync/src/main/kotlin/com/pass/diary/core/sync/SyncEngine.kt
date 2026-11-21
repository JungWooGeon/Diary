package com.pass.diary.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What a sync_state row remembers per entity (entry id, photo id, or "settings"/"entitlement"). */
data class SyncState(val fileId: String?, val syncedUpdatedAt: Long)

/** Something on this device that Drive does not have yet. */
sealed interface Pending {
    val entityId: String
    data class Entry(val doc: EntryDoc) : Pending { override val entityId get() = doc.id }
    /** Photos are immutable: uploaded once, never updated. */
    data class Photo(override val entityId: String) : Pending
    /** settings.json / entitlement.json; [json] must contain a numeric "updatedAt". */
    data class Doc(val name: DocName, val json: String, val updatedAt: Long) : Pending { override val entityId get() = name.id }
}

enum class DocName(val id: String, val path: String) { SETTINGS("settings", "settings.json"), ENTITLEMENT("entitlement", "entitlement.json") }

/**
 * Implemented by core:data on top of Room (entry / photo / sync_state tables) + DataStore for [meta].
 * "Pending" = local updatedAt differs from sync_state.syncedUpdatedAt (or no sync_state row; photos: no fileId).
 */
interface SyncLocalStore {
    suspend fun pending(): List<Pending>
    suspend fun syncState(entityId: String): SyncState?
    suspend fun setSyncState(entityId: String, state: SyncState)
    /** Drop all sync_state rows and the sync.* meta keys (account switch): everything becomes pending again. */
    suspend fun clearSyncState()

    suspend fun entry(id: String): EntryDoc?
    /** Write a remote version locally (including tombstones). Must not make the entry pending. */
    suspend fun applyRemoteEntry(doc: EntryDoc)
    /** "다른 기기에서 고친 내용": keep the losing side of a conflict until [keepUntil] (epoch millis). */
    suspend fun keepConflictLoser(doc: EntryDoc, keepUntil: Long)

    suspend fun docUpdatedAt(name: DocName): Long?
    suspend fun applyRemoteDoc(name: DocName, json: String)

    suspend fun hasPhoto(id: String): Boolean
    /** Photos of live entries whose file is not on this device (e.g. the DB came back via Android Auto Backup, photos did not). */
    suspend fun missingPhotos(): List<String>
    /** JPEG, long side <= 1600 px, quality 80 (resized by whoever saved it). */
    suspend fun photoBytes(id: String): ByteArray
    suspend fun savePhoto(id: String, bytes: ByteArray)

    /** Small persistent key/value (page token, device id, last backup time). */
    suspend fun meta(key: String): String?
    suspend fun setMeta(key: String, value: String?)
}

enum class SyncError { NOT_SIGNED_IN, VERIFY_FAILED, NETWORK, UNKNOWN }

data class SyncStatus(val lastBackupAt: Long? = null, val pending: Int = 0, val running: Boolean = false, val error: SyncError? = null)

class VerificationException(message: String) : java.io.IOException(message)

/**
 * Pull (changes since page token; full listing on an empty token = restore) then push (pending items, each
 * verified by re-reading md5Checksum). Conflicts: highest updatedAt wins, the loser is kept 30 days.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: DriveApi,
    private val store: SyncLocalStore,
) {
    internal var now: () -> Long = System::currentTimeMillis
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Cheap: refresh the UI's pending count / last backup time without network. */
    suspend fun refreshStatus() {
        _status.update { it.copy(pending = store.pending().size, lastBackupAt = store.meta(META_LAST_BACKUP)?.toLong()) }
    }

    /** Throws on failure (after recording it in [status]) so the worker can retry. */
    suspend fun sync(pushOnly: Boolean = false) = mutex.withLock {
        _status.update { it.copy(running = true) }
        try {
            if (!pushOnly) pull()
            push()
            _status.update { it.copy(error = null) }
        } catch (e: Exception) {
            _status.update { it.copy(error = e.toSyncError()) }
            throw e
        } finally {
            refreshStatus()
            _status.update { it.copy(running = false) }
        }
    }

    /** 계정·데이터 삭제: every file this app has in the user's Drive appDataFolder. Throws if any delete fails. */
    suspend fun deleteRemote() = mutex.withLock {
        val ids = buildList {
            var token: String? = null
            do { val page = api.listAll(token); page.files.forEach { add(it.id) }; token = page.nextPageToken } while (token != null)
        }
        ids.forEach { api.delete(it) }
    }

    // ---------- pull ----------

    private suspend fun pull() {
        pullChanges()
        fetchMissingPhotos()
    }

    /**
     * Entries arriving through [applyEntry] bring their photos, but a DB restored without its photo files (Auto Backup
     * leaves photos out) has rows whose files are gone and nothing new to pull: fetch those by photo id.
     * ponytail: one Drive lookup per missing photo per sync, also for photos that never reached Drive; cap if that shows up.
     */
    private suspend fun fetchMissingPhotos() {
        for (id in store.missingPhotos()) {
            val pf = api.findByEntityId(id) ?: continue
            store.savePhoto(id, api.download(pf.id))
            store.setSyncState(id, SyncState(pf.id, 0))
        }
    }

    private suspend fun pullChanges() {
        val token = store.meta(META_PAGE_TOKEN)
        if (token == null) {
            // Take the token BEFORE listing so nothing written meanwhile is missed (re-seeing a file is harmless).
            val start = api.startPageToken()
            var page: String? = null
            do {
                val p = api.listAll(page)
                p.files.forEach { absorb(it) }
                page = p.nextPageToken
            } while (page != null)
            store.setMeta(META_PAGE_TOKEN, start)
            return
        }
        var t: String = token
        while (true) {
            val p = try {
                api.changes(t)
            } catch (e: DriveHttpException) {
                if (e.code !in listOf(400, 404, 410)) throw e
                store.setMeta(META_PAGE_TOKEN, null) // token expired/invalid -> full re-list next time
                return pullChanges()
            }
            p.changes.forEach { c -> if (!c.removed) c.file?.let { absorb(it) } }
            p.newStartPageToken?.let { store.setMeta(META_PAGE_TOKEN, it); return }
            t = p.nextPageToken ?: return
            store.setMeta(META_PAGE_TOKEN, t) // resume mid-way after a crash
        }
    }

    private suspend fun absorb(f: DriveFile) {
        try {
            when {
                f.name.startsWith("entries/") -> mergeEntry(f, DriveJson.decodeFromString<EntryDoc>(api.download(f.id).decodeToString()))
                f.name == DocName.SETTINGS.path -> mergeDoc(DocName.SETTINGS, f)
                f.name == DocName.ENTITLEMENT.path -> mergeDoc(DocName.ENTITLEMENT, f)
                // photos are fetched on demand by the entry that references them; manifest is write-mostly
            }
        } catch (e: IllegalArgumentException) {
            // Unparseable file (half-written by another client, hand-edited): skip it rather than block all sync.
            // SerializationException is an IllegalArgumentException. A later valid write shows up as a new change.
            // Never the message: a SerializationException quotes the JSON input, i.e. diary text.
            android.util.Log.w("SyncEngine", "skipping unreadable ${f.name}: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun mergeEntry(f: DriveFile, remote: EntryDoc) {
        val local = store.entry(remote.id)
        val state = store.syncState(remote.id)
        when {
            local == null -> applyEntry(f, remote)
            remote.updatedAt == state?.syncedUpdatedAt -> Unit // our own upload echoing back, or nothing new
            // Only the remote side changed: take it even if its clock is behind ours, otherwise devices diverge.
            local.updatedAt == state?.syncedUpdatedAt -> applyEntry(f, remote)
            local == remote -> store.setSyncState(remote.id, SyncState(f.id, remote.updatedAt))
            else -> { // both sides changed since the last sync
                val keepUntil = now() + LOSER_KEEP_MS
                if (remote.updatedAt > local.updatedAt) {
                    store.keepConflictLoser(local, keepUntil)
                    applyEntry(f, remote)
                } else {
                    store.keepConflictLoser(remote, keepUntil)
                    // local stays pending and overwrites the remote file in push(); remember the fileId
                    store.setSyncState(remote.id, SyncState(f.id, state?.syncedUpdatedAt ?: -1))
                }
            }
        }
    }

    private suspend fun applyEntry(f: DriveFile, doc: EntryDoc) {
        for (p in doc.photos) if (!store.hasPhoto(p.id)) {
            api.findByEntityId(p.id)?.let { pf ->
                store.savePhoto(p.id, api.download(pf.id))
                store.setSyncState(p.id, SyncState(pf.id, 0))
            }
        }
        store.applyRemoteEntry(doc)
        store.setSyncState(doc.id, SyncState(f.id, doc.updatedAt))
    }

    private suspend fun mergeDoc(name: DocName, f: DriveFile) {
        val json = api.download(f.id).decodeToString()
        val remoteAt = Json.parseToJsonElement(json).jsonObject["updatedAt"]?.jsonPrimitive?.long ?: 0
        val localAt = store.docUpdatedAt(name)
        if (localAt == null || remoteAt > localAt) {
            store.applyRemoteDoc(name, json)
            store.setSyncState(name.id, SyncState(f.id, remoteAt))
        }
    }

    // ---------- push ----------

    private suspend fun push() {
        // Photos first, so another device never sees an entry whose photo is not on Drive yet.
        for (p in store.pending().sortedBy { it !is Pending.Photo }) {
            when (p) {
                is Pending.Entry -> upload(p.entityId, p.doc.path, "entry", JSON, DriveJson.encodeToString(EntryDoc.serializer(), p.doc).encodeToByteArray(), p.doc.updatedAt)
                is Pending.Photo -> upload(p.entityId, "photos/${p.entityId}.jpg", "photo", JPEG, store.photoBytes(p.entityId), 0)
                is Pending.Doc -> upload(p.entityId, p.name.path, p.name.id, JSON, p.json.encodeToByteArray(), p.updatedAt)
            }
        }
        writeManifest()
        store.setMeta(META_LAST_BACKUP, now().toString()) // = "everything on this device is verified on Drive as of now"
    }

    private suspend fun upload(entityId: String, name: String, kind: String, mime: String, bytes: ByteArray, updatedAt: Long) {
        val known = store.syncState(entityId)?.fileId ?: api.findByEntityId(entityId)?.id
        val file = if (known == null) {
            api.create(name, mapOf(DriveFile.PROP_ENTITY to entityId, DriveFile.PROP_KIND to kind), mime, bytes)
        } else try {
            api.update(known, name, mime, bytes)
        } catch (e: DriveHttpException) {
            if (e.code != 404) throw e // file was removed on Drive (e.g. user cleared app data there)
            api.create(name, mapOf(DriveFile.PROP_ENTITY to entityId, DriveFile.PROP_KIND to kind), mime, bytes)
        }
        // Verify: the response AND a fresh read of the metadata must carry our md5. Otherwise it is "not backed up".
        val md5 = md5(bytes)
        if (file.md5Checksum != md5 || api.metadata(file.id).md5Checksum != md5) {
            throw VerificationException("md5 mismatch for $name")
        }
        store.setSyncState(entityId, SyncState(file.id, updatedAt))
    }

    private suspend fun writeManifest() {
        if (store.meta(META_MANIFEST_OK) != null) return
        val device = store.meta(META_DEVICE_ID) ?: UUID.randomUUID().toString().also { store.setMeta(META_DEVICE_ID, it) }
        val existing = api.findByEntityId(MANIFEST)
        val old = existing?.let { DriveJson.decodeFromString<ManifestDoc>(api.download(it.id).decodeToString()) }
        if (old == null || device !in old.deviceIds) {
            val doc = ManifestDoc(1, (old?.deviceIds.orEmpty() + device).distinct(), now())
            val bytes = DriveJson.encodeToString(ManifestDoc.serializer(), doc).encodeToByteArray()
            if (existing == null) api.create("manifest.json", mapOf(DriveFile.PROP_ENTITY to MANIFEST, DriveFile.PROP_KIND to MANIFEST), JSON, bytes)
            else api.update(existing.id, "manifest.json", JSON, bytes)
        }
        store.setMeta(META_MANIFEST_OK, "1")
    }

    private fun Exception.toSyncError() = when (this) {
        is SyncAuthException -> SyncError.NOT_SIGNED_IN
        is VerificationException -> SyncError.VERIFY_FAILED
        is java.io.IOException -> SyncError.NETWORK
        else -> SyncError.UNKNOWN
    }

    companion object {
        const val META_PAGE_TOKEN = "sync.pageToken"
        const val META_LAST_BACKUP = "sync.lastBackupAt"
        const val META_DEVICE_ID = "sync.deviceId"
        private const val META_MANIFEST_OK = "sync.manifestOk"
        const val LOSER_KEEP_MS = 30L * 24 * 60 * 60 * 1000
        private const val MANIFEST = "manifest"
        private const val JSON = "application/json"
        private const val JPEG = "image/jpeg"

        fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
