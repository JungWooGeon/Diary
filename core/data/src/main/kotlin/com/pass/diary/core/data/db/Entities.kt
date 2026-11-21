package com.pass.diary.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

// ARCHITECTURE §2.1. Column names match the Drive JSON (§2.2) where they overlap.

@Entity(tableName = "entry", indices = [Index("date"), Index("updatedAt")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val time: String,
    val timeUnknown: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val title: String,
    val body: String,
    val template: String,
    val source: String,
    val weather: String?,
    val lightColor: String?,
)

@Entity(
    tableName = "entry_emotion",
    primaryKeys = ["entryId", "emotionKey"],
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("emotionKey")],
)
data class EntryEmotionEntity(val entryId: String, val emotionKey: String, val intensity: Int)

@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entryId", "kind", "value"],
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("value")],
)
data class EntryTagEntity(val entryId: String, val kind: String, val value: String, val auto: Boolean)

@Entity(
    tableName = "photo",
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class PhotoEntity(@PrimaryKey val id: String, val entryId: String, val localPath: String, val w: Int, val h: Int, val driveFileId: String?)

@Entity(
    tableName = "reply",
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
)
data class ReplyEntity(@PrimaryKey val entryId: String, val text: String, val source: String, val templateId: String?, val createdAt: Long, val reported: Boolean)

@Entity(
    tableName = "chat_message",
    foreignKeys = [ForeignKey(entity = EntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class ChatMessageEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entryId: String, val role: String, val text: String, val at: Long)

@Entity(tableName = "emotion_def")
data class EmotionDefEntity(@PrimaryKey val key: String, val name: String, val groupKey: String?, val colorHex: String, val order: Int, val custom: Boolean)

@Entity(tableName = "collection")
data class CollectionEntity(@PrimaryKey val itemKey: String, val unlockedAt: Long)

/** Losing side of a sync conflict ("다른 기기에서 고친 내용"), kept until [keepUntil]. [json] = Drive entry JSON. */
@Entity(tableName = "entry_conflict", indices = [Index("entryId")])
data class EntryConflictEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val entryId: String, val json: String, val savedAt: Long, val keepUntil: Long)

@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val entityId: String, val localVersion: Long, val remoteVersion: Long, val remoteFileId: String?)

data class EntryWithDetails(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId") val emotions: List<EntryEmotionEntity>,
    @Relation(parentColumn = "id", entityColumn = "entryId") val tags: List<EntryTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "entryId") val photos: List<PhotoEntity>,
)
