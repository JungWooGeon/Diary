package com.pass.diary.core.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** What happens when Drive is full, emptied by the user, revoked, corrupt, or only half there. */
class FailureScenariosTest {
    private val drive = FakeDrive()
    private var clock = 1_000L

    private fun device(store: FakeStore = FakeStore()) = store to SyncEngine(drive, store).also { it.now = { clock } }

    private fun doc(id: String, at: Long, photos: List<PhotoDoc> = emptyList()) =
        EntryDoc(id = id, date = "2026-11-21", time = "22:41", createdAt = 1, updatedAt = at, body = "본문 $at", photos = photos)

    private suspend fun SyncEngine.syncFails(): Exception {
        try { sync() } catch (e: Exception) { return e }
        fail("expected sync to fail"); error("")
    }

    private fun entryFiles() = drive.files.values.count { it.first.name.startsWith("entries/") }

    @Test fun driveFullSkipsPhotosBacksUpTextAndResumesWhenSpaceFrees() = runTest {
        val (store, engine) = device()
        store.photos["p1"] = ByteArray(5_000)
        store.save(doc("a", 10, listOf(PhotoDoc("p1", 1600, 1200)))); store.save(doc("b", 11))
        drive.capacity = 2_000 // room for the text, not the photo

        assertTrue((engine.syncFails() as DriveHttpException).isStorageFull)
        assertEquals(SyncError.STORAGE_FULL, engine.status.value.error)
        assertEquals(2, entryFiles()) // diaries went up anyway
        assertEquals(listOf<Pending>(Pending.Photo("p1")), store.pending()) // the photo waits
        assertEquals(setOf("a", "b"), store.entries.keys) // nothing local touched
        assertNull(engine.status.value.lastBackupAt) // not "검증됨"

        engine.setPhotosOff(true) // "사진 제외하고 일기만 백업"
        engine.sync()
        assertNull(engine.status.value.error)
        assertEquals(0, engine.status.value.pending)
        assertTrue(engine.status.value.photosOff)

        drive.capacity = Long.MAX_VALUE; engine.setPhotosOff(false)
        assertEquals(1, engine.status.value.pending)
        engine.sync()
        assertTrue(drive.byName("photos/p1.jpg").second.contentEquals(ByteArray(5_000)))
    }

    @Test fun driveCompletelyFullStopsAfterFirstRefusalAndPersistsStatus() = runTest {
        val (store, engine) = device()
        (1..20).forEach { store.save(doc("e$it", 10L + it)) }
        drive.capacity = 0
        drive.calls = 0
        engine.syncFails()
        assertTrue("no hot loop over 20 entries: ${drive.calls}", drive.calls < 10)
        assertEquals(20, store.pending().size)
        // a fresh process (worker failed hours ago) still shows why
        val again = SyncEngine(drive, store).also { it.refreshStatus() }
        assertEquals(SyncError.STORAGE_FULL, again.status.value.error)
    }

    @Test fun deletedHiddenAppDataIsFullyReBackedUpAndNothingLocalIsDeleted() = runTest {
        val (store, engine) = device()
        store.photos["p1"] = byteArrayOf(1)
        store.save(doc("a", 10, listOf(PhotoDoc("p1", 1, 1)))); store.save(doc("b", 11))
        store.docs[DocName.SETTINGS] = """{"updatedAt":5}"""; store.docAt[DocName.SETTINGS] = 5
        engine.sync()
        val before = store.entries.toMap()

        drive.deleteHiddenData()
        engine.sync() // changes feed reports removals -> audit -> everything pending -> re-uploaded

        assertEquals(before, store.entries)
        assertFalse(store.entries.values.any { it.deleted })
        assertEquals(2, entryFiles())
        assertTrue(drive.byName("photos/p1.jpg").second.contentEquals(byteArrayOf(1)))
        drive.byName("settings.json"); drive.byName("manifest.json")
        assertEquals(emptyList<Pending>(), store.pending())
    }

    @Test fun emptyDriveAfterLostTokenNeverDeletesLocal() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10)); engine.sync()
        drive.files.clear() // gone, and no change log entry either
        store.metas.remove(SyncEngine.META_PAGE_TOKEN) // token expired -> full listing of an empty Drive
        engine.sync()
        assertEquals(doc("a", 10), store.entries.getValue("a"))
        assertEquals(1, entryFiles())
    }

    @Test fun weeklyCheckHealsFilesThatSilentlyDisappeared() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10)); store.save(doc("b", 11)); engine.sync()
        engine.sync() // page token now past our own uploads
        drive.files.remove(drive.byName("entries/2026/11/a.json").first.id) // no change reported

        engine.sync()
        assertEquals(1, entryFiles()) // check not due yet
        clock += SyncEngine.AUDIT_EVERY_MS + 1
        engine.sync()
        assertEquals(2, entryFiles())
        assertEquals(doc("a", 10), DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")))
    }

    @Test fun onlyTombstonesDeleteLocally() = runTest {
        val (s1, e1) = device(); val (s2, e2) = device()
        s1.save(doc("a", 10)); s1.save(doc("b", 10)); e1.sync(); e2.sync()
        drive.files.remove(drive.byName("entries/2026/11/b.json").first.id)
        s1.save(doc("a", 20).copy(deleted = true, body = "")); e1.sync()
        e2.sync()
        assertTrue(s2.entries.getValue("a").deleted)
        assertFalse(s2.entries.getValue("b").deleted)
    }

    @Test fun revokedAccessStopsAndKeepsLocal() = runTest {
        val (store, engine) = device()
        store.save(doc("a", 10)); engine.sync()
        store.save(doc("a", 20))
        drive.failWith = SyncAuthException("revoked")
        engine.syncFails()
        assertEquals(SyncError.NOT_SIGNED_IN, engine.status.value.error)
        assertEquals(20, store.entries.getValue("a").updatedAt)
        drive.failWith = null
        engine.sync() // after "다시 연결"
        assertEquals(20, DriveJson.decodeFromString<EntryDoc>(drive.text("entries/2026/11/a.json")).updatedAt)
    }

    @Test fun corruptRemoteFilesAreSkippedAndNeverOverwriteLocal() = runTest {
        val (s1, e1) = device()
        s1.save(doc("good", 10)); e1.sync()
        drive.create("entries/2026/11/x.json", mapOf("entityId" to "x", "kind" to "entry"), "application/json", "{not json".encodeToByteArray())
        drive.update(drive.byName("manifest.json").first.id, "manifest.json", "application/json", "garbage".encodeToByteArray())
        drive.create("weird/thing.bin", emptyMap(), "application/octet-stream", byteArrayOf(0))

        val (s2, e2) = device(FakeStore().apply { save(doc("x", 5)) })
        e2.sync()
        assertEquals(doc("x", 5), s2.entries.getValue("x"))
        assertEquals(doc("good", 10), s2.entries.getValue("good"))
        assertNull(e2.status.value.error)
    }

    @Test fun bigRestoreCutOffMidListingContinuesWhereItStopped() = runTest {
        val (s1, e1) = device()
        (1..9).forEach { s1.save(doc("e$it", 10L + it)) }
        e1.sync()

        val (s2, e2) = device()
        drive.failListOnce = "4" // FakeDrive pages are 2 files
        e2.syncFails()
        assertEquals("4", s2.meta("sync.listPage"))
        drive.calls = 0
        e2.sync()
        assertEquals(s1.entries.filterValues { !it.deleted }, s2.entries)
        assertEquals(emptyList<Pending>(), s2.pending())
    }

    @Test fun restoreFromPartialDriveTakesWhatIsThere() = runTest {
        val (s1, e1) = device()
        s1.photos["p1"] = byteArrayOf(7); s1.photos["p2"] = byteArrayOf(8)
        s1.save(doc("a", 10, listOf(PhotoDoc("p1", 1, 1), PhotoDoc("p2", 1, 1)))); s1.save(doc("b", 11))
        e1.sync()
        drive.files.remove(drive.byName("photos/p2.jpg").first.id)
        drive.files.remove(drive.byName("manifest.json").first.id)

        val (s2, e2) = device()
        e2.sync()
        assertEquals(setOf("a", "b"), s2.entries.keys)
        assertTrue(s2.photos.getValue("p1").contentEquals(byteArrayOf(7)))
        assertFalse("p2" in s2.photos)
        assertNull(e2.status.value.error)
        drive.byName("manifest.json") // recreated
    }
}
