package com.pass.diary.core.sync

/** In-memory appDataFolder with a change log; page token = position in the log. */
class FakeDrive : DriveApi {
    val files = LinkedHashMap<String, Pair<DriveFile, ByteArray>>()
    private val log = ArrayList<String>()
    private var nextId = 0
    /** Simulate Drive storing different bytes than sent (corruption / truncated upload). */
    var corruptUploads = 0
    var calls = 0
    /** Bytes the user's Drive can still hold; uploads beyond it get 403 storageQuotaExceeded like the real one. */
    var capacity = Long.MAX_VALUE
    /** Throw this from every call (e.g. SyncAuthException for revoked access). */
    var failWith: Exception? = null
    /** listAll(pageToken) throws an IOException once for this token (worker killed mid-restore). */
    var failListOnce: String? = null

    /** Drive settings › Manage apps › "Delete hidden app data": every file goes, the changes feed reports removals. */
    fun deleteHiddenData() { files.keys.toList().forEach { files.remove(it); log += it } }

    fun byName(name: String) = files.values.single { it.first.name == name }
    fun text(name: String) = byName(name).second.decodeToString()

    private fun put(id: String, name: String, props: Map<String, String>, bytes: ByteArray): DriveFile {
        failWith?.let { throw it }
        val used = files.filterKeys { it != id }.values.sumOf { it.second.size.toLong() }
        if (used + bytes.size > capacity) throw DriveHttpException(403, "The user's Drive storage quota has been exceeded.", "storageQuotaExceeded")
        val stored = if (corruptUploads > 0) { corruptUploads--; bytes + 0 } else bytes
        val f = DriveFile(id, name, SyncEngine.md5(stored), "t${log.size}", props)
        files[id] = f to stored
        log += id
        return f
    }

    override suspend fun findByEntityId(entityId: String) = files.values.map { it.first }.firstOrNull { it.entityId == entityId }.also { calls++ }
    override suspend fun listAll(pageToken: String?): FilePage {
        calls++
        failWith?.let { throw it }
        if (pageToken != null && pageToken == failListOnce) { failListOnce = null; throw java.io.IOException("killed") }
        val all = files.values.map { it.first }
        val from = pageToken?.toInt() ?: 0
        val page = all.drop(from).take(2) // tiny pages to exercise paging
        return FilePage(page, (from + 2).takeIf { it < all.size }?.toString())
    }
    override suspend fun create(name: String, props: Map<String, String>, mime: String, bytes: ByteArray) = put("f${nextId++}", name, props, bytes).also { calls++ }
    override suspend fun update(fileId: String, name: String, mime: String, bytes: ByteArray): DriveFile {
        calls++
        val old = files[fileId] ?: throw DriveHttpException(404, "not found")
        return put(fileId, name, old.first.appProperties, bytes)
    }
    override suspend fun metadata(fileId: String) = files.getValue(fileId).first.also { calls++ }
    override suspend fun download(fileId: String) = files.getValue(fileId).second.also { calls++ }
    override suspend fun startPageToken() = log.size.toString().also { calls++ }
    override suspend fun delete(fileId: String) { calls++; files.remove(fileId) }
    override suspend fun changes(pageToken: String): ChangePage {
        calls++
        val from = pageToken.toInt()
        val ids = log.drop(from).take(2)
        val end = from + ids.size
        return ChangePage(
            ids.map { Change(it, files[it] == null, files[it]?.first) },
            nextPageToken = end.toString().takeIf { end < log.size },
            newStartPageToken = log.size.toString().takeIf { end >= log.size },
        )
    }
}

/** What core:data will implement with Room; enough to run two "devices" against one FakeDrive. */
class FakeStore : SyncLocalStore {
    val entries = LinkedHashMap<String, EntryDoc>()
    val states = HashMap<String, SyncState>()
    val photos = HashMap<String, ByteArray>()
    val losers = ArrayList<Pair<EntryDoc, Long>>()
    val docs = HashMap<DocName, String>()
    val docAt = HashMap<DocName, Long>()
    val metas = HashMap<String, String>()

    fun save(doc: EntryDoc) { entries[doc.id] = doc }

    override suspend fun pending(): List<Pending> =
        entries.values.filter { states[it.id]?.syncedUpdatedAt != it.updatedAt }.map { Pending.Entry(it) } +
            photos.keys.filter { states[it]?.fileId == null }.map { Pending.Photo(it) } +
            docs.keys.filter { states[it.id]?.syncedUpdatedAt != docAt[it] }.map { Pending.Doc(it, docs.getValue(it), docAt.getValue(it)) }
    override suspend fun syncState(entityId: String) = states[entityId]
    override suspend fun syncedFiles() = states.mapValues { it.value.fileId }
    override suspend fun setSyncState(entityId: String, state: SyncState) { states[entityId] = state }
    override suspend fun clearSyncState() { states.clear(); metas.keys.removeAll { it.startsWith("sync.") } }
    override suspend fun entry(id: String) = entries[id]
    override suspend fun applyRemoteEntry(doc: EntryDoc) { entries[doc.id] = doc }
    override suspend fun keepConflictLoser(doc: EntryDoc, keepUntil: Long) { losers += doc to keepUntil }
    override suspend fun docUpdatedAt(name: DocName) = docAt[name]
    override suspend fun applyRemoteDoc(name: DocName, json: String) {
        docs[name] = json
        docAt[name] = Regex("\"updatedAt\":(\\d+)").find(json)!!.groupValues[1].toLong()
    }
    override suspend fun hasPhoto(id: String) = id in photos
    override suspend fun missingPhotos() = entries.values.filterNot { it.deleted }.flatMap { it.photos }.map { it.id }.filterNot { it in photos }
    override suspend fun photoBytes(id: String) = photos.getValue(id)
    override suspend fun savePhoto(id: String, bytes: ByteArray) { photos[id] = bytes }
    override suspend fun meta(key: String) = metas[key]
    override suspend fun setMeta(key: String, value: String?) { if (value == null) metas.remove(key) else metas[key] = value }
}
