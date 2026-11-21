package com.pass.diary.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** PLAN §4: what premium unlocks among the 꾸미기 options. Pure, so it is unit-tested. */
object Gating {
    const val SUNSET_STREAK_DAYS = 14
    /** One missed day keeps a streak alive, at most once per this many days (빛 수집: 끊겨도 벌주지 않기). */
    const val GRACE_EVERY_DAYS = 7

    enum class Rule { STREAK, DAYS }

    /** 빛 수집 (PLAN §3.3): free palettes earned by recording. [need] = best streak or total recorded days. */
    data class Reward(val palette: LightPaletteId, val rule: Rule, val need: Int) {
        val condition get() = if (rule == Rule.STREAK) "${need}일 연속 기록" else "기록한 날 ${need}일"
        fun reached(p: Progress) = (if (rule == Rule.STREAK) p.bestStreak else p.days) >= need
        fun progress(p: Progress) = minOf(if (rule == Rule.STREAK) p.bestStreak else p.days, need)
    }

    val REWARDS = listOf(
        Reward(LightPaletteId.SUNSET, Rule.STREAK, SUNSET_STREAK_DAYS),
        Reward(LightPaletteId.DAWN, Rule.DAYS, 30),
        Reward(LightPaletteId.FIREFLY, Rule.DAYS, 100),
    )

    /** [bestStreak] = longest run ever ([bestStreak] fn), [days] = distinct recorded days. */
    data class Progress(val bestStreak: Int = 0, val days: Int = 0) {
        companion object {
            fun of(dates: Collection<String>) = Progress(bestStreak(dates), dates.toSet().size)
        }
    }

    fun reward(p: LightPaletteId) = REWARDS.firstOrNull { it.palette == p }

    /** Can this palette be picked now? */
    fun paletteUnlocked(p: LightPaletteId, premium: Boolean, progress: Progress, trial: LightPaletteId? = null, trialUntil: Long = 0, now: Long = 0): Boolean {
        if (premium) return true
        reward(p)?.let { return it.reached(progress) }
        return if (p.premium) p == trial && now < trialUntil else true
    }

    fun fontUnlocked(f: FontId, premium: Boolean) = premium || !f.premium

    /** What the app actually shows: a choice that is no longer unlocked (trial over, premium refunded) falls back. */
    fun effective(s: Settings, premium: Boolean, progress: Progress, now: Long): Settings = s.copy(
        palette = s.palette.takeIf { paletteUnlocked(it, premium, progress, s.paletteTrial, s.paletteTrialUntil, now) } ?: LightPaletteId.BASIC,
        font = s.font.takeIf { fontUnlocked(it, premium) } ?: FontId.GOTHIC,
    )

    /** Longest run among "YYYY-MM-DD" [dates]; a single missed day is forgiven once per [GRACE_EVERY_DAYS]. */
    fun bestStreak(dates: Collection<String>): Int {
        val days = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSortedSet()
        var best = 0; var run = 0; var prev: LocalDate? = null; var lastGrace: LocalDate? = null
        for (d in days) {
            val gap = prev?.let { ChronoUnit.DAYS.between(it, d) }
            run = when {
                gap == 1L -> run + 1
                gap == 2L && (lastGrace == null || ChronoUnit.DAYS.between(lastGrace, d) > GRACE_EVERY_DAYS) -> { lastGrace = d; run + 1 }
                else -> { lastGrace = null; 1 }
            }
            best = maxOf(best, run); prev = d
        }
        return best
    }

    /**
     * Recorded days in the run that ends today (or yesterday: an unwritten evening doesn't break it). A single missed
     * day in between is forgiven once per [GRACE_EVERY_DAYS] (same rule as [bestStreak]).
     */
    fun streak(dates: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in dates) today else today.minusDays(1)
        var n = 0; var lastGrace: LocalDate? = null
        while (true) {
            if (d in dates) { n++; d = d.minusDays(1); continue }
            val prev = d.minusDays(1)
            if (n > 0 && prev in dates && (lastGrace == null || ChronoUnit.DAYS.between(prev, lastGrace) > GRACE_EVERY_DAYS)) { lastGrace = prev; d = prev; continue }
            return n
        }
    }
}
