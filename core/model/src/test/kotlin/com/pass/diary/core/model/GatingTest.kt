package com.pass.diary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatingTest {
    @Test fun premiumPalettesNeedPremiumOrTrial() {
        assertFalse(Gating.paletteUnlocked(LightPaletteId.WINTER, premium = false, Gating.Progress(100, 100)))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.WINTER, premium = true, Gating.Progress()))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SPRING, false, Gating.Progress(), LightPaletteId.SPRING, trialUntil = 2_000, now = 1_000))
        assertFalse(Gating.paletteUnlocked(LightPaletteId.SPRING, false, Gating.Progress(), LightPaletteId.SPRING, trialUntil = 2_000, now = 2_000))
        // The trial is for the palette that was tried, not every premium one.
        assertFalse(Gating.paletteUnlocked(LightPaletteId.WINTER, false, Gating.Progress(), LightPaletteId.SPRING, trialUntil = 2_000, now = 1_000))
    }

    @Test fun sunsetIsTheStreakGift() {
        assertFalse(Gating.paletteUnlocked(LightPaletteId.SUNSET, false, Gating.Progress(13, 99)))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SUNSET, false, Gating.Progress(14, 14)))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.SUNSET, true, Gating.Progress()))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.BASIC, false, Gating.Progress()))
    }

    @Test fun lapsedChoicesFallBack() {
        val s = Settings(palette = LightPaletteId.WINTER, paletteTrial = LightPaletteId.WINTER, paletteTrialUntil = 10, font = FontId.HAND)
        val free = Gating.effective(s, premium = false, Gating.Progress(), now = 11)
        assertEquals(LightPaletteId.BASIC, free.palette)
        assertEquals(FontId.GOTHIC, free.font)
        assertEquals(s, Gating.effective(s, premium = true, Gating.Progress(), now = 11))
        assertEquals(LightPaletteId.WINTER, Gating.effective(s, false, Gating.Progress(), now = 5).palette)
        assertTrue(Gating.fontUnlocked(FontId.SERIF, false))
    }

    @Test fun collectedByRecordedDays() {
        assertFalse(Gating.paletteUnlocked(LightPaletteId.DAWN, false, Gating.Progress(30, 29)))
        assertTrue(Gating.paletteUnlocked(LightPaletteId.DAWN, false, Gating.Progress(1, 30)))
        assertFalse(Gating.paletteUnlocked(LightPaletteId.FIREFLY, false, Gating.Progress(1, 99)))
    }

    @Test fun oneMissedDayIsForgivenOncePerWeek() {
        // 1, 2, (3 missed), 4, 5 -> 4; a second miss within a week ends the run.
        assertEquals(4, Gating.bestStreak(listOf("2026-03-01", "2026-03-02", "2026-03-04", "2026-03-05")))
        assertEquals(3, Gating.bestStreak(listOf("2026-03-01", "2026-03-03", "2026-03-04", "2026-03-06")))
        val t = java.time.LocalDate.of(2026, 3, 20)
        assertEquals(4, Gating.streak(setOf(t, t.minusDays(1), t.minusDays(3), t.minusDays(4)), t))
        assertEquals(2, Gating.streak(setOf(t, t.minusDays(1), t.minusDays(4)), t)) // two missed days end it
    }

    @Test fun bestStreakIsTheLongestRun() {
        assertEquals(0, Gating.bestStreak(emptyList()))
        assertEquals(3, Gating.bestStreak(listOf("2026-01-30", "2026-01-31", "2026-02-01", "2026-02-01", "2026-02-05", "bad")))
    }
}
