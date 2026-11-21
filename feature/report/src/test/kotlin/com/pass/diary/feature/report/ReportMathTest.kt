package com.pass.diary.feature.report

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey.*
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportMathTest {
    private fun e(date: String, time: String, vararg em: EntryEmotion, tags: List<Tag> = emptyList()) =
        Entry(date = date, time = time, createdAt = 0, updatedAt = 0, emotions = em.toList(), tags = tags)
    private val walk = listOf(Tag(TagKind.ACTIVITY, "산책"))

    @Test fun `relations lift the group of tagged days over baseline`() {
        val es = (1..3).map { e("2026-11-0$it", "12:00", EntryEmotion(COMFY, 3), tags = walk) } +
            (4..9).map { e("2026-11-0$it", "12:00", EntryEmotion(TIRED, 3)) }
        val r = relations(es)
        assertEquals(listOf(Relation("산책한 날", EmotionGroup.CALM, 67)), r) // 100% vs 33%
        assertEquals("민지와 만난 날", tagDayLabel(TagKind.PERSON, "민지"))
        assertEquals("지민과 만난 날", tagDayLabel(TagKind.PERSON, "지민"))
    }

    @Test fun `pattern buckets by slot and weekday and summarizes`() {
        // 2026-11-16 is a Monday, 2026-11-21 a Saturday.
        val g = pattern(listOf(e("2026-11-16", "08:00", EntryEmotion(TIRED, 4)), e("2026-11-21", "13:00", EntryEmotion(JOY, 2))))
        assertEquals(EmotionGroup.WORN, g[0][0].group); assertEquals(1f, g[0][0].amount)
        assertEquals(EmotionGroup.BRIGHT, g[1][5].group); assertEquals(0.5f, g[1][5].amount)
        assertNull(g[3][3].group)
        val s = patternSummary(g)!!.joinToString("") { it.first }
        assertEquals("평일 아침엔 지침, 주말 낮엔 밝음이 많았어요.", s)
        assertEquals(3, slotOf("23:30")); assertEquals(3, slotOf("04:59"))
    }

    @Test fun `streak counts back from today or yesterday`() {
        val t = LocalDate.of(2026, 11, 21)
        assertEquals(3, streak(setOf(t, t.minusDays(1), t.minusDays(2), t.minusDays(4)), t))
        assertEquals(2, streak(setOf(t.minusDays(1), t.minusDays(2)), t))
        assertEquals(0, streak(setOf(t.minusDays(3)), t))
    }

    @Test fun `retro conditions slots and pick`() {
        val f = RetroFacts.of(
            listOf(e("2026-11-01", "12:00", EntryEmotion(COMFY, 3), tags = walk), e("2026-11-02", "23:00", EntryEmotion(COMFY, 3), tags = walk)),
            streak = 4, period = "이번 달",
        )
        assertTrue(f.matches("topGroup==calm && streak>=3"))
        assertTrue(f.matches("groupShare(calm)>=0.5"))
        assertTrue(!f.matches("trend==up"))
        assertEquals("산책으로 꽉 찬 이번 달", f.fill("{activity}(으)로 꽉 찬 {period}"))
        assertNull(f.fill("{person}(이)랑"))
        val lines = listOf(RetroLine("a", "monthly", "always", "A"), RetroLine("b", "monthly", "topGroup==calm && streak>=3", "B {streak}"))
        assertEquals("B 4", pickRetro(lines, "monthly", f, 7))
    }
}
