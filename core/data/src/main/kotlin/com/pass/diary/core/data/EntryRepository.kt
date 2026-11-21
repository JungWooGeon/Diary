package com.pass.diary.core.data

import androidx.room.withTransaction
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.data.db.EntryDao
import com.pass.diary.core.data.db.EntryEmotionEntity
import com.pass.diary.core.data.db.EntryEntity
import com.pass.diary.core.data.db.EntryTagEntity
import com.pass.diary.core.data.db.EntryWithDetails
import com.pass.diary.core.data.db.PhotoEntity
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.EntrySource
import com.pass.diary.core.model.Photo
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import com.pass.diary.core.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val db: DiaryDatabase, private val dao: EntryDao) {
    fun observeDay(date: String): Flow<List<Entry>> = dao.observeByDate(date).map { it.map(EntryWithDetails::toModel) }
    fun observeRange(from: String, to: String): Flow<List<Entry>> = dao.observeRange(from, to).map { it.map(EntryWithDetails::toModel) }
    fun observe(id: String): Flow<Entry?> = dao.observe(id).map { it?.toModel() }
    suspend fun get(id: String): Entry? = dao.get(id)?.toModel()

    /** Text matches title, body or a tag; blank text + null emotion = everything. */
    fun search(text: String, emotion: EmotionKey?): Flow<List<Entry>> {
        val pattern = "%" + text.trim().replace("\\", "").replace("%", "").replace("_", "") + "%"
        return dao.search(pattern, emotion?.key).map { it.map(EntryWithDetails::toModel) }
    }

    /**
     * Writes [entry] with a fresh updatedAt and lightColor; returns what was stored. A changed body drops Oni's reply:
     * it answered (and crisis-checked) the old text, so the reply screen runs the whole pipeline again.
     */
    suspend fun save(entry: Entry, now: Long = System.currentTimeMillis()): Entry = db.withTransaction {
        val oldBody = dao.bodyOf(entry.id)
        write(entry.copy(updatedAt = now)).also { if (oldBody != null && oldBody != entry.body) dao.clearReply(entry.id) }
    }

    /**
     * Writes entries that come from elsewhere (v1 migration, Drive) as they are: updatedAt is kept so they don't
     * look like local edits. One transaction.
     */
    suspend fun saveExternal(entries: List<Entry>) = db.withTransaction { entries.forEach { write(it) } }

    private suspend fun write(entry: Entry): Entry {
        val stored = entry.copy(lightColor = LightMixer.mix(entry.emotions).lightHex)
        dao.save(
            stored.toEntity(),
            stored.emotions.distinctBy { it.key }.map { EntryEmotionEntity(stored.id, it.key.key, it.intensity) },
            stored.tags.map { EntryTagEntity(stored.id, it.kind.key, it.value, it.auto) },
            stored.photos.map { PhotoEntity(it.id, stored.id, it.localPath, it.w, it.h, it.driveFileId) },
        )
        return stored
    }

    /** 1-tap mood: a new entry right now with one emotion at intensity 3. */
    suspend fun quickMood(key: EmotionKey, now: Long = System.currentTimeMillis()): Entry =
        save(newEntry(now = now).copy(emotions = listOf(EntryEmotion(key))), now)

    suspend fun delete(id: String, now: Long = System.currentTimeMillis()) = dao.softDelete(id, now)

    companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        fun today(): String = LocalDate.now().format(DATE)

        fun newEntry(date: String = today(), now: Long = System.currentTimeMillis()) =
            Entry(date = date, time = LocalTime.now().format(TIME), createdAt = now, updatedAt = now)
    }
}

internal fun EntryWithDetails.toModel() = Entry(
    id = entry.id, date = entry.date, time = entry.time, timeUnknown = entry.timeUnknown, createdAt = entry.createdAt, updatedAt = entry.updatedAt,
    deleted = entry.deleted, title = entry.title, body = entry.body, template = Template.of(entry.template),
    source = EntrySource.of(entry.source), weather = entry.weather, lightColor = entry.lightColor,
    // Unknown keys (e.g. from a newer app via sync) are skipped rather than crashing.
    emotions = emotions.mapNotNull { e -> EmotionKey.of(e.emotionKey)?.let { EntryEmotion(it, e.intensity.coerceIn(1, 5)) } },
    tags = tags.map { Tag(TagKind.of(it.kind), it.value, it.auto) },
    photos = photos.map { Photo(it.id, it.entryId, it.localPath, it.w, it.h, it.driveFileId) },
)

internal fun Entry.toEntity() = EntryEntity(
    id, date, time, timeUnknown, createdAt, updatedAt, deleted, title, body, template.key, source.key, weather, lightColor,
)
