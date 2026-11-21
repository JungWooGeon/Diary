package com.pass.diary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatingTest {
    @Test fun premiumPalettesNeedPremiumOrTrial() {
        assertFalse(Gating.paletteUnlocked(LightPaletteId.WINTER, premium = false, bestStreak = 100))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.WINTER, premium = true, bestStreak = 0))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SPRING, false, 0, LightPaletteId.SPRING, trialUntil = 2_000, now = 1_000))
        assertFalse(Gating.paletteUnlocked(LightPaletteId.SPRING, false, 0, LightPaletteId.SPRING, trialUntil = 2_000, now = 2_000))
        // The trial is for the palette that was tried, not every premium one.
        assertFalse(Gating.paletteUnlocked(LightPaletteId.WINTER, false, 0, LightPaletteId.SPRING, trialUntil = 2_000, now = 1_000))
    }

    @Test fun sunsetIsTheStreakGift() {
        assertFalse(Gating.paletteUnlocked(LightPaletteId.SUNSET, false, 13))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SUNSET, false, 14))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SUNSET, true, 0))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.BASIC, false, 0))
    }

    @Test fun lapsedChoicesFallBack() {
        val s = Settings(palette = LightPaletteId.WINTER, paletteTrial = LightPaletteId.WINTER, paletteTrialUntil = 10, font = FontId.HAND)
        val free = Gating.effective(s, premium = false, bestStreak = 0, now = 11)
        assertEquals(LightPaletteId.BASIC, free.palette)
        assertEquals(FontId.GOTHIC, free.font)
        assertEquals(s, Gating.effective(s, premium = true, bestStreak = 0, now = 11))
        assertEquals(LightPaletteId.WINTER, Gating.effective(s, false, 0, now = 5).palette)
        assertTrue(Gating.fontUnlocked(FontId.SERIF, false))
    }

    @Test fun bestStreakIsTheLongestRun() {
        assertEquals(0, Gating.bestStreak(emptyList()))
        assertEquals(3, Gating.bestStreak(listOf("2026-01-30", "2026-01-31", "2026-02-01", "2026-02-01", "2026-02-05", "bad")))
    }
}
