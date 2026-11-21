package com.pass.diary.core.data

import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * 감정 AI 리포트·오늘·알림 (docs/v2/AI_GROWTH.md E8 E9 E11 E12 E13): statistics over the user's own entries, on device.
 * Pure, shared by feature:report, feature:today and core:reminder. Thresholds keep small samples from being overstated.
 */
object Insights {
    /** 아침 5–11 · 낮 11–17 · 저녁 17–22 · 밤 22–5. */
    val SLOTS = listOf("아침", "낮", "저녁", "밤")
    val DAY_NAMES = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

    fun slotOf(time: String): Int {
        val h = time.substringBefore(":").trim().toIntOrNull() ?: 0
        return when (h) { in 5..10 -> 0; in 11..16 -> 1; in 17..21 -> 2; else -> 3 }
    }

    fun hasBatchim(s: String): Boolean = s.lastOrNull()?.let { it in '가'..'힣' && (it - '가') % 28 != 0 } ?: false

    fun tagDayLabel(kind: TagKind, value: String): String = when (kind) {
        TagKind.ACTIVITY -> "${value}한 날"
        TagKind.PERSON -> "$value${if (hasBatchim(value)) "과" else "와"} 만난 날"
        TagKind.PLACE -> "${value}에 간 날"
        TagKind.FREE -> "#$value 날"
    }

    private fun weatherLabel(k: WeatherKind) = when (k) {
        WeatherKind.CLEAR -> "맑은 날"; WeatherKind.CLOUDY -> "흐린 날"; WeatherKind.FOG -> "안개 낀 날"
        WeatherKind.RAIN -> "비 오는 날"; WeatherKind.SNOW -> "눈 오는 날"; WeatherKind.STORM -> "천둥 치는 날"
    }

    /** Each recorded day's dominant group (all entries of the day mixed). */
    fun dayGroups(entries: List<Entry>): Map<String, EmotionGroup> =
        entries.groupBy { it.date }.mapNotNull { (d, l) -> LightMixer.dominantGroup(l.flatMap { it.emotions })?.let { d to it } }.toMap()

    // ------------------------------------------------------------------------------------------- E8 감정 원인 찾기

    /** "친구와 만난 날 → 설렘 +40%": [lift] = percentage points above the usual share of [group]; seen on [days] days. */
    data class Cause(val label: String, val group: EmotionGroup, val lift: Int, val days: Int)

    const val CAUSE_MIN_DAYS = 3
    /** Smaller lifts are noise on a month of diaries. */
    const val CAUSE_MIN_LIFT = 15

    /**
     * Emotion × tag · weather · weekday · time slot. A factor counts when it is on ≥ [minDays] days, the period has at
     * least twice that many recorded days, and it isn't on every day (nothing to compare). Top [max], positive lifts only.
     */
    fun causes(entries: List<Entry>, minDays: Int = CAUSE_MIN_DAYS, max: Int = 3): List<Cause> {
        val days = dayGroups(entries)
        if (days.size < minDays * 2) return emptyList()
        val base = days.values.groupingBy { it }.eachCount().mapValues { it.value / days.size.toFloat() }
        // factor label -> (day -> that day's group for it)
        val factors = LinkedHashMap<String, MutableMap<String, EmotionGroup>>()
        fun add(label: String, date: String, g: EmotionGroup?) { if (g != null) factors.getOrPut(label) { HashMap() }[date] = g }
        for ((date, dayEntries) in entries.groupBy { it.date }) {
            val dayGroup = days[date] ?: continue
            dayEntries.flatMap { it.tags }.map { it.kind to it.value }.distinct().forEach { (k, v) -> add(tagDayLabel(k, v), date, dayGroup) }
            dayEntries.firstNotNullOfOrNull { Weather.decode(it.weather) }?.let { add(weatherLabel(it.kind), date, dayGroup) }
            add(DAY_NAMES[LocalDate.parse(date).dayOfWeek.ordinal], date, dayGroup)
            dayEntries.filter { !it.timeUnknown }.groupBy { slotOf(it.time) }.forEach { (s, l) ->
                add("${SLOTS[s]}에 쓴 기록", date, LightMixer.dominantGroup(l.flatMap { it.emotions }))
            }
        }
        return factors.mapNotNull { (label, byDay) ->
            if (byDay.size < minDays || byDay.size >= days.size) return@mapNotNull null
            byDay.values.groupingBy { it }.eachCount()
                .map { (g, n) -> g to ((n / byDay.size.toFloat() - (base[g] ?: 0f)) * 100).roundToInt() }
                .maxByOrNull { it.second }?.takeIf { it.second >= CAUSE_MIN_LIFT }
                ?.let { (g, lift) -> Cause(label, g, lift, byDay.size) }
        }.sortedWith(compareByDescending<Cause> { it.lift }.thenByDescending { it.days }).take(max)
    }

    // ------------------------------------------------------------------------------------------- E9 마음 예보

    data class Forecast(val day: DayOfWeek, val slot: Int?, val group: EmotionGroup, val days: Int) {
        /** Oni's line (반말, a suggestion at most — never a diagnosis). */
        val line: String get() {
            val whenText = DAY_NAMES[day.ordinal] + (slot?.let { " ${SLOTS[it]}" } ?: "") + "엔"
            return when (group) {
                EmotionGroup.WORN -> "$whenText 지치는 날이 많았어. 오늘은 조금 일찍 쉬어 볼까?"
                EmotionGroup.LOW -> "$whenText 마음이 가라앉는 날이 많았어. 오늘은 나한테 살짝 기대도 돼."
                EmotionGroup.BRIGHT -> "$whenText 빛이 환한 날이 많았어. 오늘도 좋은 일 생기면 들려줘."
                EmotionGroup.CALM -> "$whenText 마음이 잔잔한 날이 많았어. 오늘도 편안하길."
                EmotionGroup.FLUTTER -> "$whenText 설레는 날이 많았어. 오늘은 무슨 일이 생길까?"
            }
        }
    }

    /**
     * Today's weekday pattern over the last 8 weeks: ≥ 3 of those weekdays share a group (≥ 60 %) that is ≥ 20 points
     * above usual, out of ≥ 14 recorded days. The slot is named when ≥ 60 % of those entries fall in one.
     */
    fun forecast(entries: List<Entry>, today: LocalDate): Forecast? {
        val from = today.minusWeeks(8)
        val past = entries.filter { LocalDate.parse(it.date).let { d -> d.isBefore(today) && !d.isBefore(from) } }
        val days = dayGroups(past)
        if (days.size < 14) return null
        val base = days.values.groupingBy { it }.eachCount().mapValues { it.value / days.size.toFloat() }
        val same = days.filterKeys { LocalDate.parse(it).dayOfWeek == today.dayOfWeek }
        if (same.size < 3) return null
        val (g, n) = same.values.groupingBy { it }.eachCount().maxBy { it.value }
        val share = n / same.size.toFloat()
        if (n < 3 || share < 0.6f || share - (base[g] ?: 0f) < 0.2f) return null
        val slots = past.filter { it.date in same.keys && same[it.date] == g && !it.timeUnknown }.groupingBy { slotOf(it.time) }.eachCount()
        val total = slots.values.sum()
        val slot = slots.maxByOrNull { it.value }?.takeIf { total >= 3 && it.value / total.toFloat() >= 0.6f }?.key
        return Forecast(today.dayOfWeek, slot, g, n)
    }

    // ------------------------------------------------------------------------------------------- E11 마음 체크인

    const val CHECK_IN_DAYS = 3

    /**
     * Recorded days in a row (newest first, one unrecorded day skipped) whose light was 가라앉음 or 지침, counted back from
     * today within the last week. ≥ [CHECK_IN_DAYS] = ask gently how they are.
     */
    fun heavyRun(entries: List<Entry>, today: LocalDate): Int {
        val days = dayGroups(entries.filter { !LocalDate.parse(it.date).isBefore(today.minusDays(6)) && !LocalDate.parse(it.date).isAfter(today) })
        var d = today; var n = 0; var skipped = 0
        while (!d.isBefore(today.minusDays(6))) {
            val g = days[d.toString()]
            when {
                g == EmotionGroup.LOW || g == EmotionGroup.WORN -> { n++; skipped = 0 }
                g == null && skipped == 0 -> skipped++
                else -> break
            }
            d = d.minusDays(1)
        }
        return n
    }

    // ------------------------------------------------------------------------------------------- E12 스마트 리마인더

    /**
     * When the user usually starts writing: median of each day's first entry over the last 6 weeks (≥ 7 days), rounded
     * to 30 min. Night owls: 00–04 count as after midnight of the same evening. null = not enough to learn from.
     */
    fun usualTime(entries: List<Entry>, today: LocalDate): LocalTime? {
        val from = today.minusWeeks(6)
        val firsts = entries.filter { !it.timeUnknown && LocalDate.parse(it.date).let { d -> !d.isBefore(from) && !d.isAfter(today) } }
            .groupBy { it.date }.values.mapNotNull { l -> l.minOf { it.time }.let(::minuteOf) }
        if (firsts.size < 7) return null
        val sorted = firsts.map { if (it < 4 * 60) it + 24 * 60 else it }.sorted()
        val median = sorted[sorted.size / 2]
        val rounded = ((median + 15) / 30 * 30) % (24 * 60)
        return LocalTime.of(rounded / 60, rounded % 60)
    }

    private fun minuteOf(t: String): Int? = runCatching { LocalTime.parse(t.trim()).let { it.hour * 60 + it.minute } }.getOrNull()

    // ------------------------------------------------------------------------------------------- E13 키워드 구름

    /** People · places · activities of the period by how many entries mention them ([tags] = one list per entry). */
    fun keywords(tags: List<List<Tag>>, max: Int = 14): List<Pair<Tag, Int>> =
        tags.flatMap { l -> l.filter { it.value.length > 1 }.distinctBy { it.kind to it.value } }
            .groupingBy { it.kind to it.value }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Pair<TagKind, String>, Int>> { it.value }.thenBy { it.key.second })
            .take(max).map { Tag(it.key.first, it.key.second) to it.value }
}
