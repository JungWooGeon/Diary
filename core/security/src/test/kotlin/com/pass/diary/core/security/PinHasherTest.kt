package com.pass.diary.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun `right pin verifies, wrong pin does not`() {
        val h = PinHasher.encode("1234")
        assertTrue(PinHasher.verify("1234", h))
        assertFalse(PinHasher.verify("1235", h))
        assertFalse(PinHasher.verify("", h))
    }

    @Test fun `hash is salted and never contains the pin`() {
        val a = PinHasher.encode("8361"); val b = PinHasher.encode("8361")
        assertNotEquals(a, b)
        assertFalse(a.contains("8361"))
        assertEquals(PinHasher.ITERATIONS.toString(), a.split('$')[1])
    }

    @Test fun `garbage hashes never verify`() {
        for (bad in listOf("", "1234", "pbkdf2-sha256\$x\$y\$z", "md5\$1\$AA==\$AA==")) assertFalse(PinHasher.verify("1234", bad))
    }

    @Test fun `lockout grows after repeated failures`() {
        assertEquals(0L, LockManager.lockoutMillis(4))
        assertEquals(30_000L, LockManager.lockoutMillis(5))
        assertTrue(LockManager.lockoutMillis(10) > LockManager.lockoutMillis(5))
        assertTrue(LockManager.lockoutMillis(100) <= 10 * 60_000L)
    }

    @Test fun `relock only after the timeout`() {
        assertFalse(LockManager.shouldRelock(backgroundAt = 1_000, now = 30_000, afterSec = 60))
        assertTrue(LockManager.shouldRelock(backgroundAt = 1_000, now = 61_000, afterSec = 60))
        assertTrue(LockManager.shouldRelock(backgroundAt = 1_000, now = 1_000, afterSec = 0))
        assertFalse(LockManager.shouldRelock(backgroundAt = null, now = 99_000, afterSec = 0))
    }
}
