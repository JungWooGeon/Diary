package com.pass.diary.feature.report

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey.*
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthReportTest {
    private fun entry(date: String, vararg e: EntryEmotion) = Entry(date = date, time = "12:00", createdAt = 0, updatedAt = 0, emotions = e.toList())

    @Test fun `distribution is intensity weighted and top is by count`() {
        val r = MonthReport.of(
            listOf(
                entry("2026-11-01", EntryEmotion(JOY, 3), EntryEmotion(SAD, 1)),
                entry("2026-11-01", EntryEmotion(JOY, 1)),
                entry("2026-11-02", EntryEmotion(UNSURE, 3)),
            ),
        )
        assertEquals(3, r.entries); assertEquals(2, r.days)
        assertEquals(0.8f, r.groups.first { it.first == EmotionGroup.BRIGHT }.second, 1e-6f)
        assertEquals(0.2f, r.groups.first { it.first == EmotionGroup.LOW }.second, 1e-6f)
        assertEquals(listOf(JOY to 2, SAD to 1, UNSURE to 1), r.top)
    }

    @Test fun `empty month is all zero`() {
        val r = MonthReport.of(emptyList())
        assertEquals(0, r.entries)
        assertEquals(5, r.groups.size)
        assertEquals(0f, r.groups.sumOf { it.second.toDouble() }.toFloat())
    }
}
