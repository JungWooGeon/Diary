package com.pass.diary.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Review #8: lockout runs on elapsedRealtime (+ boot count), not the wall clock the user can change. */
@RunWith(RobolectricTestRunner::class)
class LockManagerTest {
    private var elapsed = 1_000_000L
    private var boot = 7
    private fun manager() = LockManager(RuntimeEnvironment.getApplication(), { elapsed }, { boot })

    private fun lockedOut(m: LockManager) = runBlocking {
        m.setPin("1234")
        repeat(4) { m.tryUnlock("0000") }
        m.tryUnlock("0000")
    }

    @Test fun lockoutFollowsElapsedTimeAndSurvivesRestart() = runBlocking {
        val m = manager()
        assertEquals(LockManager.Attempt.LockedOut(30_000), lockedOut(m))
        elapsed += 10_000
        assertEquals(20_000, m.lockoutRemaining())
        // wall clock moved a day ahead: irrelevant. Process restart: same deadline (attempts + deadline persisted).
        assertEquals(20_000, manager().lockoutRemaining())
        assertTrue(manager().tryUnlock("1234") is LockManager.Attempt.LockedOut) // even the right PIN waits
        elapsed += 20_000
        assertEquals(0, m.lockoutRemaining())
        assertEquals(LockManager.Attempt.Ok, m.tryUnlock("1234"))
    }

    @Test fun rebootCannotSkipLockout() = runBlocking {
        val m = manager()
        lockedOut(m)
        boot += 1; elapsed = 5_000 // reboot: elapsedRealtime restarts near 0
        assertEquals(30_000, m.lockoutRemaining()) // full wait again, not "deadline long passed"
        elapsed += 30_000
        assertEquals(0, m.lockoutRemaining())
    }

    @Test fun resetPinUnlocksAndClearsAttempts() = runBlocking {
        val m = manager()
        lockedOut(m)
        m.resetPin("4321")
        assertEquals(0, m.lockoutRemaining())
        assertTrue(!m.locked.value)
        assertEquals(LockManager.Attempt.Wrong(1), m.tryUnlock("1234"))
        assertEquals(LockManager.Attempt.Ok, m.tryUnlock("4321"))
    }
}
