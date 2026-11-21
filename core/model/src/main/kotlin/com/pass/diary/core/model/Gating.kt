package com.pass.diary.core.model

/** PLAN §4: what premium unlocks among the 꾸미기 options. Pure, so it is unit-tested. */
object Gating {
    const val SUNSET_STREAK_DAYS = 14

    /** Can this palette be picked now? [bestStreak] = longest run of consecutive recorded days ever. */
    fun paletteUnlocked(p: LightPaletteId, premium: Boolean, bestStreak: Int, trial: LightPaletteId? = null, trialUntil: Long = 0, now: Long = 0): Boolean = when {
        premium -> true
        p == LightPaletteId.SUNSET -> bestStreak >= SUNSET_STREAK_DAYS
        p.premium -> p == trial && now < trialUntil
        else -> true
    }

    fun fontUnlocked(f: FontId, premium: Boolean) = premium || !f.premium

    /** What the app actually shows: a choice that is no longer unlocked (trial over, premium refunded) falls back. */
    fun effective(s: Settings, premium: Boolean, bestStreak: Int, now: Long): Settings = s.copy(
        palette = s.palette.takeIf { paletteUnlocked(it, premium, bestStreak, s.paletteTrial, s.paletteTrialUntil, now) } ?: LightPaletteId.BASIC,
        font = s.font.takeIf { fontUnlocked(it, premium) } ?: FontId.GOTHIC,
    )

    /** Longest run of consecutive days among "YYYY-MM-DD" [dates]. */
    fun bestStreak(dates: Collection<String>): Int {
        val days = dates.mapNotNull { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() }.toSortedSet()
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in days) { run = if (d == prev + 1) run + 1 else 1; best = maxOf(best, run); prev = d }
        return best
    }
}
