package com.pass.diary.core.data

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class InsightsTest {
    private fun e(date: LocalDate, time: String, key: EmotionKey, weather: String? = null, tags: List<Tag> = emptyList()) =
        Entry(date = date.toString(), time = time, createdAt = 0, updatedAt = 0, emotions = listOf(EntryEmotion(key, 3)), weather = weather, tags = tags)

    private val today = LocalDate.of(2026, 11, 27) // a Friday

    @Test fun forecastNeedsARepeatedWeekdayPattern() {
        // 8 weeks: Fridays tired at night, other days calm.
        val es = (1..56).map { i ->
            val d = today.minusDays(i.toLong())
            if (d.dayOfWeek == DayOfWeek.FRIDAY) e(d, "23:00", EmotionKey.TIRED) else e(d, "20:00", EmotionKey.COMFY)
        }
        val f = Insights.forecast(es, today)!!
        assertEquals(EmotionGroup.WORN, f.group); assertEquals(3, f.slot)
        assertEquals("금요일 밤엔 지치는 날이 많았어. 오늘은 조금 일찍 쉬어 볼까?", f.line)
        assertNull(Insights.forecast(es.take(10), today)) // too little to say
        assertNull(Insights.forecast(es, today.plusDays(1))) // Saturdays are nothing special
    }

    @Test fun heavyRunSkipsOneEmptyDay() {
        val es = listOf(e(today, "21:00", EmotionKey.SAD), e(today.minusDays(2), "21:00", EmotionKey.TIRED), e(today.minusDays(3), "21:00", EmotionKey.LONELY), e(today.minusDays(4), "21:00", EmotionKey.JOY))
        assertEquals(3, Insights.heavyRun(es, today))
        assertEquals(0, Insights.heavyRun(es, today.plusDays(3)))
    }

    @Test fun usualTimeIsTheMedianFirstEntryAndWrapsMidnight() {
        val es = (0..6).map { e(today.minusDays(it.toLong()), listOf("23:40", "00:20", "23:50", "00:10", "23:30", "01:00", "00:05")[it], EmotionKey.COMFY) }
        assertEquals(LocalTime.of(0, 0), Insights.usualTime(es, today))
        assertNull(Insights.usualTime(es.take(6), today))
    }

    @Test fun causesSeeWeatherAndKeywordsCount() {
        val es = (0..8).map { i -> val d = today.minusDays(i.toLong()); if (i < 3) e(d, "12:00", EmotionKey.LONELY, "rain|10") else e(d, "12:00", EmotionKey.JOY, "clear|20") }
        assertEquals(Insights.Cause("비 오는 날", EmotionGroup.LOW, 67, 3), Insights.causes(es).first())
        val k = Insights.keywords(listOf(listOf(Tag(TagKind.PERSON, "민지"), Tag(TagKind.PERSON, "민지")), listOf(Tag(TagKind.PERSON, "민지"), Tag(TagKind.PLACE, "카페"))))
        assertEquals(listOf(Tag(TagKind.PERSON, "민지") to 2, Tag(TagKind.PLACE, "카페") to 1), k)
    }
}
