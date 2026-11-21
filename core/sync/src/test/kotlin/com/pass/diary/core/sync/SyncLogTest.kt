package com.pass.diary.core.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/** Review #12: an unreadable entry file is logged by name + exception class, never with its (diary) content. */
@RunWith(RobolectricTestRunner::class)
class SyncLogTest {
    @Test fun unreadableEntryLogsNoDiaryText() = runTest {
        val drive = FakeDrive()
        drive.create(
            "entries/2026/11/x.json", mapOf(DriveFile.PROP_ENTITY to "x", DriveFile.PROP_KIND to "entry"), "application/json",
            """{"id":"x","date":"2026-11-21","body":"아무한테도 말 못 한 비밀""".encodeToByteArray(), // cut off mid-write
        )
        SyncEngine(drive, FakeStore()).sync()
        val logs = ShadowLog.getLogs().filter { it.tag == "SyncEngine" }
        assertTrue(logs.any { "entries/2026/11/x.json" in it.msg })
        assertTrue(logs.none { "비밀" in it.msg || it.throwable != null })
    }
}
