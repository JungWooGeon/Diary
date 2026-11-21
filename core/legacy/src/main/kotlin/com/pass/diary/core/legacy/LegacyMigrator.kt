package com.pass.diary.core.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.pass.diary.core.model.Entry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A migrated v1 diary. v1 had no time of day: [Entry.time] is "21:00" and [timeUnknown] is true. */
data class LegacyEntry(val entry: Entry, val timeUnknown: Boolean = true)

/** Implemented by core:data. Must write all entries in ONE transaction and upsert by [Entry.id]. */
fun interface LegacyEntrySink {
    suspend fun upsertLegacy(entries: List<LegacyEntry>)
}

data class LegacyMigrationResult(
    val migrated: Int,
    val skippedBadDate: Int,
    val unknownEmoticons: Int,
    val droppedImageUris: Int,
)

/**
 * v1 -> v2, run once at startup before anything opens the v2 DB.
 *
 * Crash safety: the v1 file is never written. We read a *copy* (so its WAL is applied without touching the
 * original), upsert with deterministic ids (re-running is harmless), and only then rename
 * `diary` -> `diary.migrated`. That rename is the "done" marker; until it happens the next launch simply redoes it.
 */
@Singleton
class LegacyMigrator(
    private val dbFile: File,
    private val sink: LegacyEntrySink,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    @Inject constructor(@ApplicationContext context: Context, sink: LegacyEntrySink) :
        this(context.getDatabasePath(V1_DB_NAME), sink)

    private val mutex = Mutex()

    /** Returns null when there is nothing to migrate. */
    suspend fun migrateIfNeeded(): LegacyMigrationResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!dbFile.exists()) {
                parkOrphanSidecars()
                return@withContext null
            }
            val (entries, result) = read()
            sink.upsertLegacy(entries)
            markMigrated()
            Log.i(TAG, "v1 migration done: $result")
            result
        }
    }

    private fun read(): Pair<List<LegacyEntry>, LegacyMigrationResult> {
        val tmp = File(dbFile.parentFile, "legacy_read_tmp").apply { deleteRecursively(); mkdirs() }
        try {
            val copy = File(tmp, dbFile.name)
            for (s in listOf("", "-wal", "-shm", "-journal")) {
                val f = File(dbFile.path + s)
                if (f.exists()) f.copyTo(File(copy.path + s), overwrite = true)
            }
            // READWRITE on the copy so SQLite can replay its WAL; the original is untouched.
            val db = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                return convert(db)
            } finally {
                db.close()
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun convert(db: SQLiteDatabase): Pair<List<LegacyEntry>, LegacyMigrationResult> {
        // 1.1 (Room v2) = DiaryEntity, 1.0 (Room v1) = Diary. Same columns.
        val table = listOf("DiaryEntity", "Diary").firstOrNull { name ->
            db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }
        } ?: return emptyList<LegacyEntry>() to LegacyMigrationResult(0, 0, 0, 0)

        val out = ArrayList<LegacyEntry>()
        var badDate = 0; var unknown = 0; var uris = 0
        db.rawQuery(
            "SELECT id, year, month, day, emoticonId1, emoticonId2, emoticonId3, imageUri, title, content FROM `$table` ORDER BY id",
            null,
        ).use { c ->
            fun int(i: Int) = if (c.isNull(i)) null else c.getInt(i)
            while (c.moveToNext()) {
                val date = runCatching {
                    LocalDate.of(c.getString(1).trim().toInt(), c.getString(2).trim().toInt(), c.getString(3).trim().toInt())
                }.getOrNull()
                if (date == null) { badDate++; continue }
                val mapped = LegacyEmoticons.map(listOf(int(4), int(5), int(6)))
                unknown += mapped.unknownIds
                if (!c.isNull(7)) uris++ // another app's content:// grant; not copyable after the update
                val at = date.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
                val title = c.getString(8).orEmpty()
                val body = c.getString(9).orEmpty()
                out += LegacyEntry(
                    Entry(
                        id = legacyId(c.getLong(0), date.toString(), title, body),
                        date = date.toString(), // ISO = YYYY-MM-DD, zero padded
                        time = "21:00",
                        createdAt = at,
                        updatedAt = at, // deterministic so a re-run (or a 2nd device) produces identical rows
                        title = title,
                        body = body,
                        emotions = mapped.emotions,
                    ),
                )
            }
        }
        if (unknown > 0) Log.w(TAG, "$unknown emoticon ids not in the v1.1 table -> unsure")
        return out to LegacyMigrationResult(out.size, badDate, unknown, uris)
    }

    private fun markMigrated() {
        val target = migratedFiles().first { !it.exists() }
        check(dbFile.renameTo(target)) { "rename ${dbFile.name} -> ${target.name} failed" }
        parkOrphanSidecars(target)
    }

    /** Keeps the WAL next to its main file; also finishes a rename that was cut off by a crash. */
    private fun parkOrphanSidecars(target: File? = null) {
        val dest = target ?: migratedFiles().takeWhile { it.exists() }.lastOrNull() ?: return
        for (s in listOf("-wal", "-shm", "-journal")) {
            val f = File(dbFile.path + s)
            if (f.exists()) f.renameTo(File(dest.path + s))
        }
    }

    /** diary.migrated, diary.migrated.2, ... (a second v1 file, e.g. restored by hand, never overwrites the first). */
    private fun migratedFiles() = generateSequence(1) { it + 1 }
        .map { File(dbFile.parentFile, V1_DB_NAME + MIGRATED_SUFFIX + if (it == 1) "" else ".$it") }

    companion object {
        const val V1_DB_NAME = "diary"
        const val MIGRATED_SUFFIX = ".migrated"
        private const val TAG = "LegacyMigrator"

        /**
         * Stable for the same v1 row (re-runs upsert, no duplicates), but rowids restart at 1 on every phone: two phones'
         * different diaries with the same rowid must not become one entry on Drive, so the content is part of the name.
         */
        internal fun legacyId(rowId: Long, date: String, title: String, body: String): String =
            UUID.nameUUIDFromBytes("v1-diary:$rowId\u0000$date\u0000$title\u0000$body".toByteArray()).toString()
    }
}
