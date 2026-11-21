package com.pass.diary.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Transaction
    @Query("SELECT * FROM entry WHERE deleted = 0 AND date = :date ORDER BY time, createdAt")
    fun observeByDate(date: String): Flow<List<EntryWithDetails>>

    /** Inclusive range, newest first. */
    @Transaction
    @Query("SELECT * FROM entry WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC, time DESC, createdAt DESC")
    fun observeRange(from: String, to: String): Flow<List<EntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    fun observe(id: String): Flow<EntryWithDetails?>

    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun get(id: String): EntryWithDetails?

    /** [query] is a LIKE pattern ("%word%" or "%"); [emotionKey] null = any. Deleted entries excluded. */
    @Transaction
    @Query(
        """SELECT * FROM entry WHERE deleted = 0
           AND (title LIKE :query OR body LIKE :query OR id IN (SELECT entryId FROM entry_tag WHERE value LIKE :query))
           AND (:emotionKey IS NULL OR id IN (SELECT entryId FROM entry_emotion WHERE emotionKey = :emotionKey))
           ORDER BY date DESC, time DESC, createdAt DESC"""
    )
    fun search(query: String, emotionKey: String?): Flow<List<EntryWithDetails>>

    /** Everything changed since [since] (incl. soft-deleted), for sync. */
    @Transaction
    @Query("SELECT * FROM entry WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<EntryWithDetails>

    @Query("SELECT body FROM entry WHERE id = :id") suspend fun bodyOf(id: String): String?
    @Query("DELETE FROM reply WHERE entryId = :id") suspend fun clearReply(id: String)
    @Upsert suspend fun upsertEntry(entry: EntryEntity)
    @Query("DELETE FROM entry_emotion WHERE entryId = :id") suspend fun clearEmotions(id: String)
    @Query("DELETE FROM entry_tag WHERE entryId = :id") suspend fun clearTags(id: String)
    @Query("DELETE FROM photo WHERE entryId = :id") suspend fun clearPhotos(id: String)
    @Insert suspend fun insertEmotions(rows: List<EntryEmotionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTags(rows: List<EntryTagEntity>)
    @Insert suspend fun insertPhotos(rows: List<PhotoEntity>)

    /** Replaces the entry and all its emotions/tags/photos in one transaction. */
    @Transaction
    suspend fun save(entry: EntryEntity, emotions: List<EntryEmotionEntity>, tags: List<EntryTagEntity>, photos: List<PhotoEntity>) {
        upsertEntry(entry)
        clearEmotions(entry.id); clearTags(entry.id); clearPhotos(entry.id)
        insertEmotions(emotions); insertTags(tags); insertPhotos(photos)
    }

    @Query("UPDATE entry SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("SELECT photo.localPath FROM photo JOIN entry ON entry.id = photo.entryId WHERE entry.deleted = 1")
    suspend fun deletedPhotoPaths(): List<String>

    /**
     * Tombstones keep only what sync needs (id, date, time, createdAt, updatedAt, deleted): the diary text, feelings,
     * tags, photos, reply and chat are wiped. Covers every tombstone, so ones written before this rule get cleaned too.
     */
    @Query(SCRUB_ENTRY) suspend fun scrubEntries()
    @Query(SCRUB_EMOTIONS) suspend fun scrubEmotions()
    @Query(SCRUB_TAGS) suspend fun scrubTags()
    @Query(SCRUB_PHOTOS) suspend fun scrubPhotos()
    @Query(SCRUB_REPLIES) suspend fun scrubReplies()
    @Query(SCRUB_CHAT) suspend fun scrubChat()

    @Transaction
    suspend fun scrubDeleted() { scrubEntries(); scrubEmotions(); scrubTags(); scrubPhotos(); scrubReplies(); scrubChat() }

    /** Hard delete of tombstones older than [before] that Drive already has (§2.1: 30 days after sync). */
    @Query("DELETE FROM entry WHERE deleted = 1 AND updatedAt < :before AND id IN (SELECT entityId FROM sync_state WHERE remoteVersion = entry.updatedAt)")
    suspend fun purgeDeleted(before: Long)

    /** Emits whenever any entry is written; drives "back up after save". */
    @Query("SELECT MAX(updatedAt) FROM entry") fun observeLastChange(): Flow<Long?>

    /** Entries Drive doesn't have in this version (no sync_state, or a different synced updatedAt). Incl. tombstones. */
    @Transaction
    @Query("SELECT * FROM entry WHERE id NOT IN (SELECT entityId FROM sync_state WHERE remoteVersion = entry.updatedAt)")
    suspend fun unsynced(): List<EntryWithDetails>

    @Query("SELECT photo.id FROM photo JOIN entry ON entry.id = photo.entryId WHERE entry.deleted = 0")
    suspend fun livePhotoIds(): List<String>

    @Query("SELECT * FROM photo WHERE id NOT IN (SELECT entityId FROM sync_state WHERE remoteFileId IS NOT NULL)")
    suspend fun unsyncedPhotos(): List<PhotoEntity>
}

@Dao
interface ReplyDao {
    @Query("SELECT * FROM reply WHERE entryId = :entryId") fun observe(entryId: String): Flow<ReplyEntity?>
    @Query("SELECT * FROM reply WHERE entryId = :entryId") suspend fun get(entryId: String): ReplyEntity?
    @Query("DELETE FROM reply WHERE entryId = :entryId") suspend fun delete(entryId: String)
    @Query("SELECT * FROM reply ORDER BY createdAt DESC") fun observeAll(): Flow<List<ReplyEntity>>
    @Upsert suspend fun upsert(reply: ReplyEntity)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_message WHERE entryId = :entryId ORDER BY at") fun observe(entryId: String): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM chat_message WHERE entryId = :entryId ORDER BY at") suspend fun get(entryId: String): List<ChatMessageEntity>
    @Query("DELETE FROM chat_message WHERE entryId = :entryId") suspend fun clear(entryId: String)
    @Insert suspend fun insert(message: ChatMessageEntity)
}

@Dao
interface EmotionDefDao {
    @Query("SELECT * FROM emotion_def ORDER BY `order`") fun observeAll(): Flow<List<EmotionDefEntity>>
    @Upsert suspend fun upsert(def: EmotionDefEntity)
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collection") fun observeAll(): Flow<List<CollectionEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun unlock(item: CollectionEntity)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE entityId = :id") suspend fun get(id: String): SyncStateEntity?
    @Upsert suspend fun upsert(state: SyncStateEntity)
    @Query("DELETE FROM sync_state") suspend fun clear()
}

@Dao
interface EntryConflictDao {
    @Query("SELECT * FROM entry_conflict WHERE keepUntil > :now ORDER BY savedAt DESC") fun observe(now: Long): Flow<List<EntryConflictEntity>>
    @Insert suspend fun insert(row: EntryConflictEntity)
    @Query("DELETE FROM entry_conflict WHERE keepUntil <= :now") suspend fun purge(now: Long)
}

// See [EntryDao.scrubDeleted]. DatabaseModule also runs these on open, for tombstones from older builds.
private const val OF_DELETED = "entryId IN (SELECT id FROM entry WHERE deleted = 1)"
internal const val SCRUB_ENTRY = "UPDATE entry SET title = '', body = '', weather = NULL, lightColor = NULL " +
    "WHERE deleted = 1 AND (title != '' OR body != '' OR weather IS NOT NULL OR lightColor IS NOT NULL)"
internal const val SCRUB_EMOTIONS = "DELETE FROM entry_emotion WHERE $OF_DELETED"
internal const val SCRUB_TAGS = "DELETE FROM entry_tag WHERE $OF_DELETED"
internal const val SCRUB_PHOTOS = "DELETE FROM photo WHERE $OF_DELETED"
internal const val SCRUB_REPLIES = "DELETE FROM reply WHERE $OF_DELETED"
internal const val SCRUB_CHAT = "DELETE FROM chat_message WHERE $OF_DELETED"
internal val SCRUB_DELETED = listOf(SCRUB_ENTRY, SCRUB_EMOTIONS, SCRUB_TAGS, SCRUB_PHOTOS, SCRUB_REPLIES, SCRUB_CHAT)
