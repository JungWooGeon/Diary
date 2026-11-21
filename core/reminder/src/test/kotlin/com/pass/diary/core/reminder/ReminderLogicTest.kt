package com.pass.diary.core.reminder

import com.pass.diary.core.reminder.ReminderLogic.next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class ReminderLogicTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val ny = ZoneId.of("America/New_York")
    private val all = DayOfWeek.entries.toSet()
    private val ten = LocalTime.of(22, 0)
    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun laterTodayOrTomorrow() {
        assertEquals(at(seoul, 2026, 11, 21, 22), next(at(seoul, 2026, 11, 21, 21, 58), ten, all))
        // At (and right after) the alarm time → tomorrow, so the fired alarm doesn't re-arm itself.
        assertEquals(at(seoul, 2026, 11, 22, 22), next(at(seoul, 2026, 11, 21, 22), ten, all))
        // Year rollover.
        assertEquals(at(seoul, 2027, 1, 1, 22), next(at(seoul, 2026, 12, 31, 23), ten, all))
    }

    @Test fun chosenDaysOnly() {
        // 2026-11-21 is a Saturday.
        assertEquals(at(seoul, 2026, 11, 23, 22), next(at(seoul, 2026, 11, 21, 8), ten, setOf(MONDAY, WEDNESDAY, FRIDAY)))
        assertEquals(at(seoul, 2026, 11, 21, 22), next(at(seoul, 2026, 11, 21, 8), ten, setOf(SATURDAY)))
        assertEquals(at(seoul, 2026, 11, 28, 22), next(at(seoul, 2026, 11, 21, 23), ten, setOf(SATURDAY)))
    }

    @Test fun dstZones() {
        // New York springs forward 2026-03-08 02:00 → 03:00: a 02:30 reminder that day fires at 03:30 local.
        val t = next(at(ny, 2026, 3, 7, 23), LocalTime.of(2, 30), all)
        assertEquals(LocalTime.of(3, 30), t.toLocalTime()); assertEquals(8, t.dayOfMonth)
        // Across the change 22:00 stays 22:00 local (23h between the two alarms, not 24h).
        val before = next(at(ny, 2026, 3, 7, 21), ten, all)
        val after = next(before, ten, all)
        assertEquals(ten, after.toLocalTime())
        assertEquals(23, java.time.Duration.between(before, after).toHours())
        // Fall back 2026-11-01: 01:30 happens twice; the first one is used.
        assertEquals(-4 * 3600, next(at(ny, 2026, 10, 31, 23), LocalTime.of(1, 30), all).offset.totalSeconds)
        // Same wall-clock time in another zone.
        assertEquals(ten, next(at(ZoneId.of("Europe/London"), 2026, 6, 1, 23), ten, all).toLocalTime())
    }

    @Test fun skipIfWritten() {
        val p = ReminderPrefs(enabled = true)
        assertTrue(ReminderLogic.shouldRemind(p, MONDAY, writtenToday = false))
        assertFalse(ReminderLogic.shouldRemind(p, MONDAY, writtenToday = true))
        assertTrue(ReminderLogic.shouldRemind(p.copy(skipIfWritten = false), MONDAY, writtenToday = true))
        assertFalse(ReminderLogic.shouldRemind(p.copy(days = setOf(FRIDAY)), MONDAY, writtenToday = false))
        assertFalse(ReminderLogic.shouldRemind(p.copy(enabled = false), MONDAY, writtenToday = false))
    }

    @Test fun alarmDays() {
        assertEquals(null, ReminderPrefs().alarmDays)
        assertEquals(setOf(FRIDAY), ReminderPrefs(enabled = true, days = setOf(FRIDAY)).alarmDays)
        assertEquals(all, ReminderPrefs(enabled = true, days = setOf(FRIDAY), yearAgo = true).alarmDays)
    }

    @Test fun noRepeatWithin14() {
        assertTrue(ReminderLogic.LINES.size >= 40)
        assertEquals(ReminderLogic.LINES.size, ReminderLogic.LINES.toSet().size)
        val r = Random(7)
        var recent = emptyList<Int>()
        val sent = (1..200).map { ReminderLogic.pick(ReminderLogic.LINES.size, recent, r).also { recent = ReminderLogic.remember(recent, it) } }
        sent.windowed(ReminderLogic.NO_REPEAT + 1).forEach { w -> assertEquals(w.size, w.toSet().size) }
        // Pool smaller than the window still yields something.
        assertNotEquals(-1, ReminderLogic.pick(3, listOf(0, 1, 2)))
    }

    @Test fun labels() {
        assertEquals("오후 10:00", ReminderLogic.timeLabel(22, 0))
        assertEquals("오전 12:05", ReminderLogic.timeLabel(0, 5))
        assertEquals("오후 12:30", ReminderLogic.timeLabel(12, 30))
        assertEquals("평일", ReminderLogic.daysLabel(DayOfWeek.entries.take(5).toSet()))
        assertEquals("월·수·금", ReminderLogic.daysLabel(setOf(FRIDAY, MONDAY, WEDNESDAY)))
    }
}
