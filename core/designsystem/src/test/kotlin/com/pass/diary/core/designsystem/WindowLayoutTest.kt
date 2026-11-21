package com.pass.diary.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    private fun l(w: Int, h: Int) = WindowLayout.of(w, h).let { Triple(it.rail, it.twoPane, it.large) }

    @Test fun phonePortraitKeepsTheBottomBar() {
        assertEquals(Triple(false, false, false), l(412, 915))
        assertEquals(68f, WindowLayout.of(412, 915).bottomBar.value)
    }

    @Test fun landscapePhoneGetsRailAndPanesButPhoneType() {
        assertEquals(Triple(true, true, false), l(915, 412))
        assertEquals(true, WindowLayout.of(915, 412).short)
        assertEquals(false, WindowLayout.of(412, 915).short)
    }

    @Test fun tabletPortraitIsOnePaneWithRail() = assertEquals(Triple(true, false, true), l(800, 1280))

    @Test fun tabletLandscapeAndUnfoldedFoldAreTwoPane() {
        assertEquals(Triple(true, true, true), l(1280, 800))
        assertEquals(Triple(true, true, true), l(841, 701))
        assertEquals(0f, WindowLayout.of(1280, 800).bottomBar.value)
    }

    @Test fun unfoldedFoldPortraitIsOnePane() = assertEquals(Triple(true, false, true), l(701, 841))
}
