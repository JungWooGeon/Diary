package com.pass.diary.core.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreResultTest {
    @Test fun mapsPlayAnswer() {
        assertEquals(RestoreResult.FAILED, restoreResult(ok = false, owned = true))
        assertEquals(RestoreResult.RESTORED, restoreResult(ok = true, owned = true))
        assertEquals(RestoreResult.NOTHING, restoreResult(ok = true, owned = false))
    }

    @Test fun onlyFirstResultIsDelivered() {
        val got = mutableListOf<RestoreResult>()
        val once = OnceResult { got += it }
        once(RestoreResult.RESTORED); once(RestoreResult.FAILED)
        assertEquals(listOf(RestoreResult.RESTORED), got)
    }
}
