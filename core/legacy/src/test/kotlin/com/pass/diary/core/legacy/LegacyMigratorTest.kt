package com.pass.diary.core.legacy

import android.database.sqlite.SQLiteDatabase
import com.pass.diary.core.model.EmotionKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class LegacyMigratorTest {
    @get:Rule val tmp = TemporaryFolder()
    private val seoul = ZoneId.of("Asia/Seoul")

    private class Sink : LegacyEntrySink {
        val rows = LinkedHashMap<String, LegacyEntry>()
        var failNext = false
        override suspend fun upsertLegacy(entries: List<LegacyEntry>) {
            if (failNext) { failNext = false; error("process killed") }
            entries.forEach { rows[it.entry.id] = it }
        }
    }

    private fun fixtureDir(): File = tmp.newFolder("databases").also {
        File("../../tools/legacy/fixture_v1.db").copyTo(File(it, "diary"))
    }

    @Test fun migratesFixture() = runTest {
        val dir = fixtureDir()
        val sink = Sink()
        val r = LegacyMigrator(File(dir, "diary"), sink, seoul).migrateIfNeeded()!!

        assertEquals(LegacyMigrationResult(migrated = 11, skippedBadDate = 1, unknownEmoticons = 1, droppedImageUris = 1), r)
        val byTitle = sink.rows.values.associateBy { it.entry.title }
        byTitle.getValue("봄").entry.let {
            assertEquals("2024-03-05", it.date)
            assertEquals("21:00", it.time)
            assertEquals(java.time.ZonedDateTime.of(2024, 3, 5, 21, 0, 0, 0, seoul).toInstant().toEpochMilli(), it.createdAt)
            assertEquals(listOf(EmotionKey.JOY), it.emotions.map { e -> e.key })
        }
        assertTrue(sink.rows.values.all { it.timeUnknown })
        assertEquals(listOf(EmotionKey.JOY, EmotionKey.EXCITED, EmotionKey.LOVE), byTitle.getValue("친구").entry.emotions.map { it.key })
        assertEquals("엄마랑 카페\n두 줄", byTitle.getValue("친구").entry.body)
        assertEquals(emptyList<Any>(), byTitle.getValue("사진만").entry.emotions)
        assertEquals(emptyList<Any>(), byTitle.getValue("없음").entry.emotions)
        assertEquals(listOf(EmotionKey.UNSURE, EmotionKey.JOY), byTitle.getValue("모르는 id").entry.emotions.map { it.key })
        assertEquals("2024-02-29", byTitle.getValue("uri").entry.date)

        assertFalse(File(dir, "diary").exists())
        assertTrue(File(dir, "diary.migrated").exists())
        assertFalse(File(dir, "legacy_read_tmp").exists())
        // second launch: nothing to do
        assertNull(LegacyMigrator(File(dir, "diary"), sink, seoul).migrateIfNeeded())
    }

    @Test fun crashBeforeRenameIsRetriedWithoutDuplicates() = runTest {
        val dir = fixtureDir()
        val sink = Sink().apply { failNext = true }
        val m = LegacyMigrator(File(dir, "diary"), sink, seoul)
        runCatching { m.migrateIfNeeded() }
        assertTrue(File(dir, "diary").exists()) // not marked done
        m.migrateIfNeeded()
        m.migrateIfNeeded()
        assertEquals(11, sink.rows.size)
        // ids are stable across runs (and devices)
        val again = Sink()
        File(dir, "diary.migrated").copyTo(File(dir, "diary"))
        LegacyMigrator(File(dir, "diary"), again, seoul).migrateIfNeeded()
        assertEquals(sink.rows.keys, again.rows.keys)
        assertTrue(File(dir, "diary.migrated.2").exists())
    }

    @Test fun readsV10TableAndUncheckpointedWal() = runTest {
        val src = tmp.newFolder("live")
        val live = SQLiteDatabase.openOrCreateDatabase(File(src, "diary"), null)
        live.enableWriteAheadLogging()
        live.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { it.moveToFirst() }
        live.execSQL(
            "CREATE TABLE `Diary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `year` TEXT NOT NULL, `month` TEXT NOT NULL, `day` TEXT NOT NULL, " +
                "`dayOfWeek` TEXT NOT NULL, `emoticonId1` INTEGER, `emoticonId2` INTEGER, `emoticonId3` INTEGER, `imageUri` TEXT, `title` TEXT NOT NULL, `content` TEXT NOT NULL)",
        )
        live.execSQL("INSERT INTO Diary VALUES (1,'2023','9','1','금',${0x7f100007},NULL,NULL,NULL,'v1.0','슬펐다')")
        // Simulate the v1 process being killed: copy files while the DB is open, rows only in the WAL.
        val dir = tmp.newFolder("databases")
        for (s in listOf("", "-wal", "-shm")) File(src, "diary$s").takeIf { it.exists() }?.copyTo(File(dir, "diary$s"))
        live.close()
        assertTrue(File(dir, "diary-wal").exists())

        val sink = Sink()
        val r = LegacyMigrator(File(dir, "diary"), sink, seoul).migrateIfNeeded()!!
        assertEquals(1, r.migrated)
        assertEquals("2023-09-01", sink.rows.values.single().entry.date)
        assertEquals(EmotionKey.SAD, sink.rows.values.single().entry.emotions.single().key)
        assertTrue(File(dir, "diary.migrated").exists())
        assertTrue(File(dir, "diary.migrated-wal").exists()) // WAL kept next to its main file
        assertFalse(File(dir, "diary-wal").exists())
    }

    /** Review #9: rowid 1 on two phones is two different diaries; the same row on one phone keeps its id. */
    @Test fun idsDifferAcrossDevicesForSameRowId() {
        val a = LegacyMigrator.legacyId(1, "2024-03-05", "봄", "벚꽃")
        assertEquals(a, LegacyMigrator.legacyId(1, "2024-03-05", "봄", "벚꽃"))
        assertTrue(a != LegacyMigrator.legacyId(1, "2024-03-05", "봄", "다른 폰의 일기"))
        assertTrue(a != LegacyMigrator.legacyId(1, "2024-03-06", "봄", "벚꽃"))
        assertTrue(a != LegacyMigrator.legacyId(2, "2024-03-05", "봄", "벚꽃"))
        // field boundaries count: ("ab","c") is not ("a","bc")
        assertTrue(LegacyMigrator.legacyId(1, "d", "ab", "c") != LegacyMigrator.legacyId(1, "d", "a", "bc"))
    }

    @Test fun noV1DbIsNoop() = runTest {
        assertNull(LegacyMigrator(File(tmp.newFolder(), "diary"), Sink(), seoul).migrateIfNeeded())
    }
}
