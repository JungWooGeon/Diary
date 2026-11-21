package com.pass.diary.core.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class TrashArchiveTest {
    private val drive = FakeDrive()
    private var clock = 1_000L
    private fun device(store: FakeStore = FakeStore()) = store to SyncEngine(drive, store).also { it.now = { clock } }

    private fun doc(id: String, at: Long, body: String = "본문", photos: List<PhotoDoc> = emptyList()) =
        EntryDoc(id = id, date = "2026-11-21", time = "22:41", createdAt = 1, updatedAt = at, title = "제목", body = body,
            emotions = listOf(EmotionDoc("joy", 3)), photos = photos, reply = ReplyDoc("답장", "template", null, 2))

    private fun fixture(name: String) = DriveJson.decodeFromString<EntryDoc>(File("../../shared/fixtures/$name").readText())

    @Test fun deleteGoesToTrashOnDriveAndRestoreComesBackOnTheOtherDevice() = runTest {
        val (phone, phoneSync) = device()
        val (tablet, tabletSync) = device()
        phone.save(doc("a", 10)); phoneSync.sync()
        tabletSync.sync()

        phone.save(phone.entries.getValue("a").copy(deleted = true, trashedAt = 20, updatedAt = 20)); phoneSync.sync()
        val onDrive = DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json"))
        assertEquals(20L, onDrive.trashedAt); assertEquals("본문", onDrive.body) // content kept on Drive while in trash
        assertTrue(drive.text("entries/2026/11/a.json").contains("\"trashedAt\":20"))

        tabletSync.sync()
        assertEquals(onDrive, tablet.entries["a"]) // in the tablet's 휴지통, with its content
        tablet.save(tablet.entries.getValue("a").copy(deleted = false, trashedAt = null, updatedAt = 30)); tabletSync.sync()

        phoneSync.sync()
        val back = phone.entries.getValue("a")
        assertFalse(back.deleted); assertNull(back.trashedAt); assertEquals("본문", back.body); assertEquals("답장", back.reply?.text)
        assertFalse(drive.text("entries/2026/11/a.json").contains("trashedAt")) // live files never carry the field
        assertTrue(phone.losers.isEmpty() && tablet.losers.isEmpty())
    }

    @Test fun sameExpiryOnTwoDevicesIsNotAConflict() = runTest {
        // Both devices expire the same trash entry to the same file (updatedAt = trashedAt + 30 days in EntryDao).
        val (phone, phoneSync) = device()
        val (tablet, tabletSync) = device()
        phone.save(doc("a", 20).copy(deleted = true, trashedAt = 20)); phoneSync.sync(); tabletSync.sync()
        val expired = EntryDoc(id = "a", date = "2026-11-21", time = "22:41", createdAt = 1, updatedAt = 20 + 30L * 86_400_000, deleted = true)
        phone.save(expired); tablet.save(expired)
        phoneSync.sync(); tabletSync.sync()
        assertTrue(phone.losers.isEmpty() && tablet.losers.isEmpty())
        assertEquals(expired, DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")))
    }

    @Test fun sharedFixturesReadTheSameAsTheWeb() {
        val trashed = fixture("entry-trashed.json")
        assertTrue(trashed.deleted); assertEquals(1763600000000L, trashed.trashedAt); assertEquals("오늘도 애썼어", trashed.reply?.text)
        assertEquals(trashed.trashedAt, trashed.toEntry { it }.trashedAt)
        val tomb = fixture("entry-tombstone.json")
        assertTrue(tomb.deleted); assertNull(tomb.trashedAt); assertNull(tomb.toEntry { it }.trashedAt)
        // Re-encoding a trashed doc keeps the field; a tombstone gains no trashedAt.
        assertEquals(trashed, DriveJson.decodeFromString<EntryDoc>(DriveJson.encodeToString(EntryDoc.serializer(), trashed)))
        assertFalse(DriveJson.encodeToString(EntryDoc.serializer(), tomb).contains("trashedAt"))
    }

    private suspend fun export(store: FakeStore, includeTrash: Boolean) = ByteArrayOutputStream().also { DiaryArchive.export(store, it, includeTrash, now = 99) }.toByteArray()

    @Test fun exportThenImportRestoresEverythingWithoutDrive() = runTest {
        val old = FakeStore()
        old.save(doc("a", 10, photos = listOf(PhotoDoc("p1", 4, 3))))
        old.photos["p1"] = byteArrayOf(9, 8, 7)
        old.save(doc("t", 11, body = "휴지통 글").copy(deleted = true, trashedAt = 11))
        old.save(EntryDoc(id = "gone", date = "2026-01-01", time = "09:00", createdAt = 1, updatedAt = 5, deleted = true))
        val zip = export(old, includeTrash = true)

        val names = ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertEquals(listOf("entries.json", "photos/p1.jpg", "diary.html", "README.txt"), names)

        val fresh = FakeStore()
        assertEquals(ImportResult(added = 2, updated = 0, skipped = 0), DiaryArchive.import(fresh) { ByteArrayInputStream(zip) })
        assertEquals(old.entries["a"], fresh.entries["a"]); assertEquals(old.entries["t"], fresh.entries["t"])
        assertNull(fresh.entries["gone"]) // tombstones aren't exported
        assertTrue(fresh.photos.getValue("p1").contentEquals(byteArrayOf(9, 8, 7)))
        // Imported entries are pending, so they go to Drive with the next backup.
        assertEquals(setOf("a", "t", "p1"), fresh.pending().map { it.entityId }.toSet())

        val noTrash = FakeStore()
        val zipNoTrash = export(old, includeTrash = false)
        DiaryArchive.import(noTrash) { ByteArrayInputStream(zipNoTrash) }
        assertEquals(setOf("a"), noTrash.entries.keys)
    }

    @Test fun importNeverOverwritesNewerLocalEdits() = runTest {
        val old = FakeStore()
        old.save(doc("a", 10, body = "옛 글")); old.save(doc("b", 10, body = "옛 글"))
        val zip = export(old, includeTrash = false)
        val now = FakeStore()
        now.save(doc("a", 50, body = "새로 고친 글")) // edited after the export
        now.save(doc("b", 5, body = "더 옛날 글"))
        assertEquals(ImportResult(added = 0, updated = 1, skipped = 1), DiaryArchive.import(now) { ByteArrayInputStream(zip) })
        assertEquals("새로 고친 글", now.entries.getValue("a").body)
        assertEquals("옛 글", now.entries.getValue("b").body)
    }

    @Test fun importsTheWebsZip() = runTest {
        // shared/fixtures/web-export.zip = web/src/archive.ts exportZip([entry-trashed.json]) (stored, UTF-8 names).
        val zip = File("../../shared/fixtures/web-export.zip").readBytes()
        val store = FakeStore()
        assertEquals(ImportResult(1, 0, 0), DiaryArchive.import(store) { ByteArrayInputStream(zip) })
        assertEquals(fixture("entry-trashed.json"), store.entries.values.single())
    }

    @Test fun importRejectsUnsafeIds() = runTest {
        val evil = FakeStore()
        evil.save(doc("../../x", 10)); evil.save(doc("ok", 10, photos = listOf(PhotoDoc("../p", 1, 1))))
        val fresh = FakeStore()
        val zip = export(evil, false)
        assertEquals(ImportResult(0, 0, 2), DiaryArchive.import(fresh) { ByteArrayInputStream(zip) })
        assertTrue(fresh.entries.isEmpty())
    }

    @Test fun htmlIsReadableAndEscaped() {
        val html = DiaryArchive.html(listOf(doc("a", 10, body = "<script>x</script>\n둘째 줄", photos = listOf(PhotoDoc("p1", 1, 1)))), setOf("p1"))
        assertTrue("2026년 11월 21일" in html); assertTrue("기쁨" in html); assertTrue("photos/p1.jpg" in html)
        assertFalse("<script>x" in html); assertTrue("&lt;script&gt;" in html)
    }
}
