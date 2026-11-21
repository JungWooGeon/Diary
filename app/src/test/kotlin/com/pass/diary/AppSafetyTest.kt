package com.pass.diary

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSafetyTest {
    /** Review #7: with a PIN, the recents thumbnail is hidden even when "화면 캡처 막기" is off. */
    @Test fun pinLockAlwaysHidesRecents() {
        assertEquals(SecureWindow(flagSecure = false, recentsScreenshot = true), secureWindow(secureScreen = false, pinLock = false, sdk = 34))
        assertEquals(SecureWindow(flagSecure = false, recentsScreenshot = false), secureWindow(secureScreen = false, pinLock = true, sdk = 34))
        assertEquals(SecureWindow(flagSecure = true, recentsScreenshot = false), secureWindow(secureScreen = false, pinLock = true, sdk = 31))
        assertTrue(secureWindow(secureScreen = true, pinLock = false, sdk = 34).flagSecure)
    }

    /** Review #10: a refused foreground start (ForegroundServiceStartNotAllowedException is an IllegalStateException) = retry. */
    @Test fun refusedForegroundMeansRetry() = runBlocking {
        assertFalse(foregroundOrRetry { throw IllegalStateException("startForeground() not allowed") })
        assertTrue(foregroundOrRetry { })
    }
}
