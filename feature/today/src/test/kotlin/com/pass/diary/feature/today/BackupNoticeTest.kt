package com.pass.diary.feature.today

import com.pass.diary.core.sync.SyncError
import com.pass.diary.core.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupNoticeTest {
    private val now = 100 * DAY_MS
    private fun on(s: SyncStatus, dismissedAt: Long? = null) = backupNotice(true, s, now, dismissedAt, null, false)

    @Test fun troubleOnlyWhenBackupIsOn() {
        assertEquals(BackupNotice.QUOTA, on(SyncStatus(error = SyncError.STORAGE_FULL, pending = 3)))
        assertEquals(BackupNotice.AUTH, on(SyncStatus(error = SyncError.NOT_SIGNED_IN)))
        assertEquals(BackupNotice.STALE, on(SyncStatus(lastBackupAt = now - 4 * DAY_MS, pending = 1)))
        assertNull(on(SyncStatus(lastBackupAt = now - 4 * DAY_MS, pending = 0))) // old but nothing to upload
        assertNull(on(SyncStatus(lastBackupAt = now - 2 * DAY_MS, pending = 5)))
        assertNull(on(SyncStatus(error = SyncError.NETWORK, lastBackupAt = now - HOUR_MS, pending = 1))) // offline for a bit: calm
        // never the hint while backup is on
        assertNull(on(SyncStatus()))
    }

    @Test fun dismissHidesFor24Hours() {
        val full = SyncStatus(error = SyncError.STORAGE_FULL)
        assertNull(on(full, dismissedAt = now - 23 * HOUR_MS))
        assertEquals(BackupNotice.QUOTA, on(full, dismissedAt = now - 25 * HOUR_MS))
    }

    @Test fun hintOnceAWeekWhenBackupIsOff() {
        fun off(hintAt: Long?, never: Boolean = false) = backupNotice(false, SyncStatus(error = SyncError.STORAGE_FULL), now, null, hintAt, never)
        assertEquals(BackupNotice.HINT, off(null))
        assertEquals(BackupNotice.HINT, off(now - 2 * HOUR_MS)) // the day it came up
        assertNull(off(now - 2 * DAY_MS))
        assertEquals(BackupNotice.HINT, off(now - 7 * DAY_MS))
        assertNull(off(null, never = true))
    }
}
