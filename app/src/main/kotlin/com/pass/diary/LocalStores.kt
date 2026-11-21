package com.pass.diary

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ChatMessageDao
import com.pass.diary.core.data.db.ChatMessageEntity
import com.pass.diary.core.data.db.EntryConflictDao
import com.pass.diary.core.data.db.EntryConflictEntity
import com.pass.diary.core.data.db.EntryDao
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.data.db.SyncStateDao
import com.pass.diary.core.data.db.SyncStateEntity
import com.pass.diary.core.legacy.LegacyEntry
import com.pass.diary.core.legacy.LegacyEntrySink
import com.pass.diary.core.model.Entry
import com.pass.diary.core.sync.ChatDoc
import com.pass.diary.core.sync.DocName
import com.pass.diary.core.sync.DriveJson
import com.pass.diary.core.sync.EntryDoc
import com.pass.diary.core.sync.Pending
import com.pass.diary.core.sync.ReplyDoc
import com.pass.diary.core.sync.SyncLocalStore
import com.pass.diary.core.sync.SyncState
import com.pass.diary.core.sync.toDoc
import com.pass.diary.core.sync.toEntry
import com.pass.diary.core.billing.Billing
import com.pass.diary.core.data.SettingsRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncMeta by preferencesDataStore("sync_meta")

/**
 * core:sync's view of the local data (Room + files/photos + a small DataStore for meta). Lives in :app because
 * core:sync depends on core:data, not the other way round.
 */
@Singleton
class RoomSyncLocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository,
    private val entries: EntryDao,
    private val replies: ReplyDao,
    private val chat: ChatMessageDao,
    private val states: SyncStateDao,
    private val conflicts: EntryConflictDao,
    private val settings: SettingsRepository,
    private val billing: Billing,
) : SyncLocalStore {
    private val photoDir get() = File(context.filesDir, "photos").apply { mkdirs() }
    private fun photoFile(id: String) = File(photoDir, "$id.jpg")

    override suspend fun pending(): List<Pending> =
        entries.unsynced().map { Pending.Entry(docOf(it.entry.id)!!) } +
            entries.unsyncedPhotos().filter { photoFile(it.id).exists() }.map { Pending.Photo(it.id) } +
            listOfNotNull(settingsDoc(), entitlementDoc()).filter { d -> states.get(d.entityId)?.remoteVersion != d.updatedAt }

    /** settings.json once anything synced was changed on this phone (theme, 꾸미기). */
    private suspend fun settingsDoc(): Pending.Doc? {
        val s = settings.settings.first()
        return if (s.updatedAt == 0L) null else Pending.Doc(DocName.SETTINGS, settings.toJson(s), s.updatedAt)
    }

    /** entitlement.json (ARCHITECTURE §2.2) for the web, only once Play has confirmed a purchase. Play stays the truth on Android. */
    private fun entitlementDoc(): Pending.Doc? {
        val e = billing.entitlement
        if (!e.premium || e.updatedAt == 0L) return null
        val json = buildJsonObject {
            put("premium", true); put("orderId", e.orderId); put("purchasedAt", e.purchasedAt); put("updatedAt", e.updatedAt)
        }.toString()
        return Pending.Doc(DocName.ENTITLEMENT, json, e.updatedAt)
    }

    override suspend fun syncState(entityId: String) = states.get(entityId)?.let { SyncState(it.remoteFileId, it.remoteVersion) }
    override suspend fun setSyncState(entityId: String, state: SyncState) =
        states.upsert(SyncStateEntity(entityId, localVersion = state.syncedUpdatedAt, remoteVersion = state.syncedUpdatedAt, remoteFileId = state.fileId))

    override suspend fun clearSyncState() {
        states.clear()
        context.syncMeta.edit { p -> p.asMap().keys.filter { it.name.startsWith("sync.") }.forEach { p.remove(it) } }
    }

    override suspend fun entry(id: String): EntryDoc? = docOf(id)

    private suspend fun docOf(id: String): EntryDoc? {
        val e = repository.get(id) ?: return null
        val reply = replies.get(id)?.let { ReplyDoc(it.text, it.source, it.templateId, it.createdAt, it.reported) }
        return e.toDoc(e.timeUnknown, reply, chat.get(id).map { ChatDoc(it.role, it.text, it.at) })
    }

    override suspend fun applyRemoteEntry(doc: EntryDoc) {
        val entry: Entry = doc.toEntry { photoFile(it).absolutePath }.copy(timeUnknown = doc.timeUnknown)
        repository.saveExternal(listOf(entry)) // keeps updatedAt -> not pending
        replies.delete(doc.id)
        doc.reply?.let { replies.upsert(ReplyEntity(doc.id, it.text, it.source, it.templateId, it.createdAt, it.reported)) }
        chat.clear(doc.id)
        doc.chat.forEach { chat.insert(ChatMessageEntity(entryId = doc.id, role = it.role, text = it.text, at = it.at)) }
    }

    override suspend fun keepConflictLoser(doc: EntryDoc, keepUntil: Long) =
        conflicts.insert(EntryConflictEntity(entryId = doc.id, json = DriveJson.encodeToString(EntryDoc.serializer(), doc), savedAt = System.currentTimeMillis(), keepUntil = keepUntil))

    override suspend fun docUpdatedAt(name: DocName): Long? = when (name) {
        DocName.SETTINGS -> settings.settings.first().updatedAt.takeIf { it > 0 }
        DocName.ENTITLEMENT -> billing.entitlement.updatedAt.takeIf { it > 0 }
    }

    override suspend fun applyRemoteDoc(name: DocName, json: String) = when (name) {
        DocName.SETTINGS -> settings.applyRemote(json)
        // Play restores the purchase by itself on a new phone; the remote copy is kept only for reference.
        DocName.ENTITLEMENT -> setMeta("doc.${name.id}", json)
    }

    override suspend fun hasPhoto(id: String) = photoFile(id).exists()
    override suspend fun missingPhotos() = withContext(Dispatchers.IO) { entries.livePhotoIds().filterNot { photoFile(it).exists() } }
    override suspend fun photoBytes(id: String) = withContext(Dispatchers.IO) { photoFile(id).readBytes() }
    override suspend fun savePhoto(id: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val tmp = File(photoDir, "$id.jpg.part")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(photoFile(id)))
    }

    override suspend fun meta(key: String): String? = context.syncMeta.data.first()[stringPreferencesKey(key)]
    override suspend fun setMeta(key: String, value: String?) {
        context.syncMeta.edit { if (value == null) it.remove(stringPreferencesKey(key)) else it[stringPreferencesKey(key)] = value }
    }
}

/** v1 migration writes through the repository in one transaction, keeping the deterministic ids/updatedAt. */
class RoomLegacySink @Inject constructor(private val repository: EntryRepository) : LegacyEntrySink {
    override suspend fun upsertLegacy(entries: List<LegacyEntry>) =
        repository.saveExternal(entries.map { it.entry.copy(timeUnknown = it.timeUnknown) })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalStoreModule {
    @Binds abstract fun syncStore(impl: RoomSyncLocalStore): SyncLocalStore
    @Binds abstract fun legacySink(impl: RoomLegacySink): LegacyEntrySink
}
