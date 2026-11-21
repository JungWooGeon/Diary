package com.pass.diary.core.sync

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.EntrySource
import com.pass.diary.core.model.Photo
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import com.pass.diary.core.model.Template
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire format of entries/YYYY/MM/<id>.json — must match shared/schema/entry.schema.json. */
@Serializable
data class EntryDoc(
    val id: String,
    val date: String,
    val time: String,
    val timeUnknown: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    /** 휴지통: deleted=true with content kept until trashedAt + 30 days. Omitted (null) on live entries and hard tombstones. */
    val trashedAt: Long? = null,
    val title: String = "",
    val body: String = "",
    val template: String = "free",
    val source: String = "text",
    val weather: String? = null,
    val emotions: List<EmotionDoc> = emptyList(),
    val tags: List<TagDoc> = emptyList(),
    val photos: List<PhotoDoc> = emptyList(),
    val reply: ReplyDoc? = null,
    val chat: List<ChatDoc> = emptyList(),
) {
    /** Deleted for good (not in the 휴지통): nothing left to lose. */
    val isTombstone: Boolean get() = deleted && trashedAt == null

    /** Logical Drive path (= file name in the flat appDataFolder, see manifest.schema.json). */
    val path: String get() = "entries/${date.substring(0, 4)}/${date.substring(5, 7)}/$id.json"
}

@Serializable data class EmotionDoc(val key: String, val intensity: Int)
@Serializable data class TagDoc(val kind: String, val value: String, val auto: Boolean = false)
@Serializable data class PhotoDoc(val id: String, val w: Int, val h: Int)
@Serializable data class ReplyDoc(val text: String, val source: String, val templateId: String? = null, val createdAt: Long, val reported: Boolean = false)
@Serializable data class ChatDoc(val role: String, val text: String, val at: Long)

@Serializable data class ManifestDoc(val schema: Int = 1, val deviceIds: List<String> = emptyList(), val updatedAt: Long = 0)

/** Shared Json config: tolerant reader (web may add fields), defaults written so files are self-describing. */
val DriveJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

fun Entry.toDoc(timeUnknown: Boolean = false, reply: ReplyDoc? = null, chat: List<ChatDoc> = emptyList()) = EntryDoc(
    id = id, date = date, time = time, timeUnknown = timeUnknown, createdAt = createdAt, updatedAt = updatedAt, deleted = deleted, trashedAt = trashedAt,
    title = title, body = body, template = template.key, source = source.key, weather = weather,
    emotions = emotions.map { EmotionDoc(it.key.key, it.intensity) },
    tags = tags.map { TagDoc(it.kind.key, it.value, it.auto) },
    photos = photos.map { PhotoDoc(it.id, it.w, it.h) },
    reply = reply, chat = chat,
)

/** [localPhotoPath] resolves where a photo lives (or will be downloaded to) on this device. */
fun EntryDoc.toEntry(localPhotoPath: (String) -> String) = Entry(
    id = id, date = date, time = time, createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
    trashedAt = trashedAt?.takeIf { deleted }, title = title, body = body, template = Template.of(template), source = EntrySource.of(source), weather = weather,
    // Unknown key (newer client) -> unsure rather than dropping the user's feeling; clamp out-of-range intensity.
    emotions = emotions.map { EntryEmotion(EmotionKey.of(it.key) ?: EmotionKey.UNSURE, it.intensity.coerceIn(1, 5)) }.distinctBy { it.key },
    tags = tags.map { Tag(TagKind.of(it.kind), it.value, it.auto) },
    photos = photos.map { Photo(id = it.id, entryId = id, localPath = localPhotoPath(it.id), w = it.w, h = it.h) },
)
