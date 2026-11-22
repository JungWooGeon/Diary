package com.pass.diary

import com.pass.diary.ReviewRules.DAY_MS
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    private val first = 1_000 * DAY_MS
    private val now = first + 10 * DAY_MS

    private fun ask(
        now: Long = this.now, lastPromptAt: Long = 0, streak: Int = 0, total: Int = 5,
        positive: Boolean = true, crisis: Boolean = false,
    ) = shouldAskReview(now, first, lastPromptAt, streak, total, positive, crisis)

    @Test fun `positive entry with 5 entries asks`() = assertTrue(ask())
    @Test fun `positive entry with 4 entries waits`() = assertFalse(ask(total = 4))
    @Test fun `7-day streak asks even on a neutral entry`() = assertTrue(ask(streak = 7, total = 1, positive = false))
    @Test fun `6-day streak and neutral entry waits`() = assertFalse(ask(streak = 6, positive = false))
    @Test fun `never in the first 3 days`() = assertFalse(ask(now = first + 3 * DAY_MS - 1, streak = 7))
    @Test fun `day 3 is fine`() = assertTrue(ask(now = first + 3 * DAY_MS))
    @Test fun `crisis entry never asks`() = assertFalse(ask(streak = 30, crisis = true))
    @Test fun `once per 60 days`() {
        assertFalse(ask(lastPromptAt = now - 60 * DAY_MS + 1))
        assertTrue(ask(lastPromptAt = now - 60 * DAY_MS))
    }

    private fun entry(vararg e: EntryEmotion) = Entry(date = "2026-11-22", time = "21:00", createdAt = 0, updatedAt = 0, emotions = e.toList())

    @Test fun `top emotion decides positivity`() {
        assertTrue(entry(EntryEmotion(EmotionKey.JOY, 4), EntryEmotion(EmotionKey.SAD, 2)).topEmotionPositive())
        assertFalse(entry(EntryEmotion(EmotionKey.JOY, 2), EntryEmotion(EmotionKey.SAD, 4)).topEmotionPositive())
        assertTrue(entry(EntryEmotion(EmotionKey.SERENE)).topEmotionPositive())
        assertFalse(entry(EntryEmotion(EmotionKey.MISS)).topEmotionPositive())
        assertFalse(entry(EntryEmotion(EmotionKey.UNSURE)).topEmotionPositive())
        assertFalse(entry().topEmotionPositive())
    }
}
