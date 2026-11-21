package com.pass.diary.core.reminder

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

data class ReminderPrefs(
    val enabled: Boolean = false,
    val hour: Int = 22,
    val minute: Int = 0,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val skipIfWritten: Boolean = true,
    val yearAgo: Boolean = false,
    /** E12: follow the time the user usually writes ([Insights.usualTime]) instead of [hour]:[minute]. Picking a time turns it off. */
    val auto: Boolean = false,
    /** E10: 일요일 밤 주간 온이 편지 알림. */
    val weekly: Boolean = true,
    /** E9: 마음 예보 알림, at most once a week. */
    val forecast: Boolean = true,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)
    /** 1년 전 오늘 is checked every day, so it keeps the alarm daily whatever [days] says. */
    val alarmDays: Set<DayOfWeek>? get() = when { yearAgo -> DayOfWeek.entries.toSet(); enabled -> days; else -> null }
}

object ReminderLogic {
    /** Recent lines not to repeat (one reminder a day → about two weeks). */
    const val NO_REPEAT = 14

    /**
     * First [time] strictly after [now] on one of [days], in [now]'s zone. A time that falls in a DST gap moves
     * forward by the gap (ZonedDateTime.of), an overlap takes the earlier offset.
     */
    fun next(now: ZonedDateTime, time: LocalTime, days: Set<DayOfWeek>): ZonedDateTime {
        require(days.isNotEmpty())
        var d = now.toLocalDate()
        while (true) {
            val t = ZonedDateTime.of(d, time, now.zone)
            if (t.isAfter(now) && d.dayOfWeek in days) return t
            d = d.plusDays(1)
        }
    }

    fun shouldRemind(p: ReminderPrefs, day: DayOfWeek, writtenToday: Boolean): Boolean =
        p.enabled && day in p.days && !(p.skipIfWritten && writtenToday)

    /** Index of the next line: random among those not in the last [NO_REPEAT] sent. */
    fun pick(size: Int, recent: List<Int>, random: Random = Random): Int {
        val avoid = recent.takeLast(NO_REPEAT).toSet()
        return (0 until size).filter { it !in avoid }.ifEmpty { (0 until size).toList() }.random(random)
    }

    fun remember(recent: List<Int>, sent: Int): List<Int> = (recent + sent).takeLast(NO_REPEAT)

    /** 주간 온이 편지: Sunday at this time. */
    val LETTER_TIME: LocalTime = LocalTime.of(21, 0)
    /** 마음 예보: checked at noon, so it lands before the evening it is about. */
    val FORECAST_TIME: LocalTime = LocalTime.of(12, 0)
    const val FORECAST_EVERY_DAYS = 7

    /** Next E9/E10 check, or null when both are off. */
    fun nextInsight(now: ZonedDateTime, p: ReminderPrefs): ZonedDateTime? = listOfNotNull(
        if (p.weekly) next(now, LETTER_TIME, setOf(DayOfWeek.SUNDAY)) else null,
        if (p.forecast) next(now, FORECAST_TIME, DayOfWeek.entries.toSet()) else null,
    ).minOrNull()

    /** Which one a check at [at] is for. The letter wins on Sunday evening. */
    fun isLetterTime(at: ZonedDateTime) = at.dayOfWeek == DayOfWeek.SUNDAY && at.hour >= 20

    /** "오후 10:00" */
    fun timeLabel(hour: Int, minute: Int) = "%s %d:%02d".format(if (hour < 12) "오전" else "오후", (hour + 11) % 12 + 1, minute)

    fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
        DayOfWeek.entries.toSet() -> "매일"
        DayOfWeek.entries.take(5).toSet() -> "평일"
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "주말"
        else -> days.sorted().joinToString("·") { DAY_NAMES[it.ordinal] }
    }

    val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

    /** Oni's voice (character README §3): gentle 반말, one question at most, never about missing days or streaks. */
    val LINES = listOf(
        "불 켜 둘게, 오늘 얘기 들려줘.",
        "오늘 하루 어땠어? 한 줄이어도 좋아.",
        "하루 끝에 잠깐, 나랑 얘기할래?",
        "오늘 제일 기억에 남는 순간은 뭐였어?",
        "수고 많았어. 기분 하나만 골라 줘도 충분해.",
        "따뜻한 차 한 잔 같은 시간, 같이 보낼래?",
        "오늘은 무슨 색 빛이었을까?",
        "별일 없던 하루도 적어 두면 반짝여.",
        "나 여기 불 켜 두고 기다리고 있어.",
        "오늘 마음 날씨는 어땠어?",
        "길게 안 써도 돼. 생각나는 만큼만.",
        "오늘 웃었던 일 하나만 알려 줄래?",
        "잠들기 전에 오늘을 살짝 내려놓자.",
        "오늘의 너를 조금 기억해 두고 싶어.",
        "바빴던 하루였다면, 이제 좀 쉬어도 돼.",
        "오늘 고마웠던 거 하나 떠올려 볼까?",
        "하루가 저물었어. 나랑 천천히 정리해 볼래?",
        "오늘 들은 말 중에 마음에 남은 게 있어?",
        "기분만 톡 남겨도 내가 그 색으로 켜질게.",
        "오늘 하루한테 몇 점 주고 싶어?",
        "오늘 먹은 것 중에 제일 맛있던 건 뭐야?",
        "힘든 날이었다면 그냥 털어놔도 괜찮아.",
        "좋은 날이었다면 같이 기억해 두자.",
        "밤공기가 조용해. 얘기 들려줄래?",
        "오늘 문득 떠오른 사람 있어?",
        "지금 마음에 제일 가까운 기분은 뭐야?",
        "하루치 이야기, 내가 잘 간직해 둘게.",
        "오늘은 어떤 장면이 제일 선명해?",
        "작은 일도 괜찮아. 나한텐 다 소중해.",
        "하루 끝 5분, 너한테 선물하자.",
        "오늘 나한테 하고 싶은 말 있어?",
        "내일의 네가 반가워할 한 줄, 남겨 볼래?",
        "오늘 하루도 무사히 지나왔네. 잘했어.",
        "불 끄기 전에 잠깐 들렀다 가.",
        "오늘 설렜던 순간이 있었어?",
        "지친 하루였으면 짧게 쓰고 푹 자자.",
        "오늘은 어떤 노래가 어울리는 하루였어?",
        "마음속에 남은 거, 여기 두고 가도 돼.",
        "오늘의 빛을 같이 찾아보자.",
        "네 하루가 궁금해서 불 켜 봤어.",
        "쓰고 싶은 날에 쓰면 돼. 오늘은 어때?",
        "오늘 스스로 칭찬해 주고 싶은 거 있어?",
        "조용한 밤이야. 천천히 얘기해 줘.",
        "오늘 하루를 한 단어로 말하면 뭘까?",
        "잘 지냈어? 오늘 얘기 듣고 싶어.",
    )
}
