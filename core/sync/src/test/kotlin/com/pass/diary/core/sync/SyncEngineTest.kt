package com.pass.diary.core.sync

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncEngineTest {
    private val drive = FakeDrive()
    private var clock = 1_000L

    private fun device(store: FakeStore = FakeStore()) = store to SyncEngine(drive, store).also { it.now = { clock } }

    private fun doc(id: String, at: Long, body: String = "본문 $at", date: String = "2026-11-21", photos: List<PhotoDoc> = emptyList()) =
        EntryDoc(id = id, date = date, time = "22:41", createdAt = 1, updatedAt = at, body = body,
            emotions = listOf(EmotionDoc("joy", 3)), photos = photos)

    @Test fun firstBackupUploadsEverythingWithLayoutAndVerification() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10))
        store.save(doc("b", 11, date = "2025-01-03"))
        store.photos["p1"] = byteArrayOf(1, 2, 3)
        store.docs[DocName.SETTINGS] = """{"updatedAt":5,"theme":"dark"}"""; store.docAt[DocName.SETTINGS] = 5

        engine.sync()

        assertEquals(doc("a", 10), DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")))
        assertNotNull(drive.byName("entries/2025/01/b.json"))
        assertTrue(drive.byName("photos/p1.jpg").second.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals("""{"updatedAt":5,"theme":"dark"}""", drive.text("settings.json"))
        assertEquals("entry", drive.byName("entries/2026/11/a.json").first.kind)
        val manifest = DriveJson.decodeFromString<ManifestDoc>(drive.text("manifest.json"))
        assertEquals(1, manifest.schema); assertEquals(1, manifest.deviceIds.size)
        assertEquals(SyncStatus(lastBackupAt = clock, pending = 0, running = false, error = null), engine.status.value)
        assertEquals(emptyList<Pending>(), store.pending())
    }

    @Test fun stickerPngUploadsAsPngUnderTheSameJpgName() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        assertEquals("image/png", SyncEngine.imageMime(png))
        assertEquals("image/jpeg", SyncEngine.imageMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals("image/jpeg", SyncEngine.imageMime(byteArrayOf(1, 2, 3)))
    }

    @Test fun incrementalUploadsOnlyTheChangeAndRenamesOnDateChange() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10)); store.save(doc("b", 10))
        engine.sync()
        val fileCount = drive.files.size
        drive.calls = 0

        store.save(doc("a", 20, body = "고침", date = "2026-12-01"))
        engine.sync(pushOnly = true)

        assertEquals(fileCount, drive.files.size) // updated in place, not duplicated
        assertEquals("고침", DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/12/a.json")).body)
        assertTrue(drive.files.values.none { it.first.name == "entries/2026/11/a.json" })
        assertEquals(2, drive.calls) // update + metadata re-read; b untouched, manifest cached
    }

    @Test fun restoreToEmptyDeviceIncludingPhotosAndSettings() = runTest {
        val (s1, e1) = device()
        s1.photos["p1"] = byteArrayOf(9, 9)
        s1.save(doc("a", 10, photos = listOf(PhotoDoc("p1", 1600, 1200))))
        s1.save(doc("b", 11)); s1.save(doc("c", 12))
        s1.docs[DocName.ENTITLEMENT] = """{"premium":true,"updatedAt":7}"""; s1.docAt[DocName.ENTITLEMENT] = 7
        e1.sync()

        val (s2, e2) = device()
        e2.sync()

        assertEquals(s1.entries, s2.entries)
        assertTrue(s2.photos.getValue("p1").contentEquals(byteArrayOf(9, 9)))
        assertEquals(s1.docs, s2.docs)
        assertEquals(emptyList<Pending>(), s2.pending()) // nothing to re-upload
        assertNotNull(s2.meta(SyncEngine.META_PAGE_TOKEN))
    }

    @Test fun changesFromAnotherDeviceArriveViaPageToken() = runTest {
        val (s1, e1) = device(); val (s2, e2) = device()
        e1.sync(); e2.sync() // both have tokens
        s1.save(doc("a", 10)); s1.save(doc("b", 11)); s1.save(doc("c", 12))
        e1.sync()
        e2.sync()
        assertEquals(setOf("a", "b", "c"), s2.entries.keys)
        assertEquals(2, DriveJson.decodeFromString<ManifestDoc>(drive.text("manifest.json")).deviceIds.size)
    }

    @Test fun conflictLastWriteWinsAndLoserIsKept30Days() = runTest {
        val (s1, e1) = device(); val (s2, e2) = device()
        s1.save(doc("a", 10)); e1.sync(); e2.sync()

        s1.save(doc("a", 20, body = "폰1")); s2.save(doc("a", 30, body = "폰2")) // both edit offline
        e1.sync()
        e2.sync() // s2 newer: keeps its own, remote "폰1" becomes the loser
        e1.sync() // s1 gets "폰2", nothing lost

        assertEquals("폰2", s1.entries.getValue("a").body)
        assertEquals("폰2", s2.entries.getValue("a").body)
        assertEquals("폰2", DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")).body)
        assertEquals(listOf("폰1"), s2.losers.map { it.first.body })
        assertEquals(clock + SyncEngine.LOSER_KEEP_MS, s2.losers.single().second)
        assertTrue(s1.losers.isEmpty()) // s1 had nothing unsynced when "폰2" arrived
    }

    @Test fun conflictWhereRemoteIsNewerKeepsLocalAsLoser() = runTest {
        val (s1, e1) = device(); val (s2, e2) = device()
        s1.save(doc("a", 10)); e1.sync(); e2.sync()
        s2.save(doc("a", 20, body = "옛날")); s1.save(doc("a", 30, body = "최신"))
        e1.sync(); e2.sync()
        assertEquals("최신", s2.entries.getValue("a").body)
        assertEquals(listOf("옛날"), s2.losers.map { it.first.body })
        assertEquals(emptyList<Pending>(), s2.pending())
    }

    @Test fun deleteTravelsAsTombstone() = runTest {
        val (s1, e1) = device(); val (s2, e2) = device()
        s1.save(doc("a", 10)); e1.sync(); e2.sync()
        s1.save(doc("a", 20).copy(deleted = true, body = "", title = ""))
        e1.sync(); e2.sync()
        assertTrue(s2.entries.getValue("a").deleted)
        assertTrue(DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")).deleted) // file kept
    }

    @Test fun deleteRemoteRemovesEveryFile() = runTest {
        val (store, engine) = device()
        (1..5).forEach { store.save(doc("e$it", 10L + it)) } // > one page in FakeDrive
        engine.sync()
        assertTrue(drive.files.size >= 5)
        engine.deleteRemote()
        assertTrue(drive.files.isEmpty())
    }

    @Test fun scrubbedTombstoneRoundTrips() {
        // What EntryRepository.delete leaves behind: no text/feelings/tags/photos, only the sync fields.
        val t = com.pass.diary.core.model.Entry(id = "a", date = "2026-11-03", time = "21:00", createdAt = 1, updatedAt = 9, deleted = true)
        val json = DriveJson.encodeToString(EntryDoc.serializer(), t.toDoc())
        val back = DriveJson.decodeFromString<EntryDoc>(json)
        assertTrue(back.deleted)
        assertEquals("entries/2026/11/a.json", back.path)
        assertEquals(listOf(9L, 1L), listOf(back.updatedAt, back.createdAt))
        assertEquals(t, back.toEntry { it })
    }

    @Test fun verificationFailureIsReportedThenRetrySucceeds() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10))
        drive.corruptUploads = 1
        try { engine.sync(); fail("expected VerificationException") } catch (_: VerificationException) {}
        assertEquals(SyncError.VERIFY_FAILED, engine.status.value.error)
        assertEquals(1, engine.status.value.pending) // "백업 안 됨" stays visible
        assertNull(engine.status.value.lastBackupAt)

        engine.sync() // the worker's retry
        assertNull(engine.status.value.error)
        assertEquals(0, engine.status.value.pending)
        assertEquals(1, drive.files.values.count { it.first.name.startsWith("entries/") }) // reused the file, no duplicate
        assertEquals(doc("a", 10), DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")))
    }

    @Test fun fileRemovedOnDriveIsRecreated() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10)); engine.sync()
        drive.files.clear()
        store.save(doc("a", 11)); engine.sync(pushOnly = true)
        assertEquals(11, DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")).updatedAt)
    }

    @Test fun reinstallWithLocalCopyDoesNotDuplicateOrConflict() = runTest {
        val (s1, e1) = device()
        s1.save(doc("a", 10)); e1.sync()
        // e.g. v1 migration produced the same deterministic entry on a second device
        val (s2, e2) = device(FakeStore().apply { save(doc("a", 10)) })
        e2.sync()
        assertTrue(s2.losers.isEmpty())
        assertEquals(1, drive.files.values.count { it.first.name.startsWith("entries/") })
        assertEquals(emptyList<Pending>(), s2.pending())
    }

    /** Review #4: Auto Backup restores the DB (and maybe an old page token) but not photo files; pull fetches them. */
    @Test fun restoredDbWithoutPhotoFilesRefetchesThem() = runTest {
        val (s1, e1) = device()
        s1.photos["p1"] = byteArrayOf(4, 2)
        s1.save(doc("a", 10, photos = listOf(PhotoDoc("p1", 1600, 1200))))
        e1.sync()
        // worst case: even sync_state and the page token came back, so there is nothing new to pull
        val restored = FakeStore().apply { entries.putAll(s1.entries); states.putAll(s1.states); metas.putAll(s1.metas) }
        val (s2, e2) = device(restored)
        e2.sync()
        assertTrue(s2.photos.getValue("p1").contentEquals(byteArrayOf(4, 2)))
        assertEquals(emptyList<Pending>(), s2.pending())
    }

    @Test fun unknownEmotionKeyFromNewerClientMapsToUnsure() {
        val e = doc("a", 1).copy(emotions = listOf(EmotionDoc("nostalgic", 9), EmotionDoc("joy", 2))).toEntry { "/p/$it" }
        assertEquals(listOf(EntryEmotion(EmotionKey.UNSURE, 5), EntryEmotion(EmotionKey.JOY, 2)), e.emotions)
        val round = Entry(date = "2026-01-02", time = "09:00", createdAt = 1, updatedAt = 2, emotions = listOf(EntryEmotion(EmotionKey.LOVE, 4)))
        assertEquals(round, round.toDoc().toEntry { "/p/$it" })
        assertFalse(DriveJson.encodeToString(EntryDoc.serializer(), round.toDoc()).contains("\"reply\""))
    }
}
