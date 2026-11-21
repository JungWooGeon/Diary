package com.pass.diary.feature.report

import com.pass.diary.core.data.Insights
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.Gating
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.TagKind
import java.time.DayOfWeek
import java.time.LocalDate

/** Computed on device (PLAN §3.5). Weights are intensities; `unsure` counts in [emotions] but has no group. */
data class MonthReport(
    val entries: Int,
    val days: Int,
    /** Share of total intensity per group (0..1), all five groups in order. */
    val groups: List<Pair<EmotionGroup, Float>>,
    /** Most frequent emotions (by number of entries), at most 3. */
    val top: List<Pair<EmotionKey, Int>>,
) {
    val dominant: EmotionGroup? get() = groups.filter { it.second > 0f }.maxByOrNull { it.second }?.first

    companion object {
        fun of(entries: List<Entry>): MonthReport {
            val emotions = entries.flatMap { it.emotions }
            val byGroup = emotions.filter { it.key.group != null }.groupBy { it.key.group!! }.mapValues { (_, l) -> l.sumOf { it.intensity } }
            val total = byGroup.values.sum().toFloat()
            return MonthReport(
                entries = entries.size,
                days = entries.map { it.date }.distinct().size,
                groups = EmotionGroup.entries.map { it to if (total == 0f) 0f else (byGroup[it] ?: 0) / total },
                top = emotions.groupingBy { it.key }.eachCount().entries
                    .sortedWith(compareByDescending<Map.Entry<EmotionKey, Int>> { it.value }.thenBy { it.key.ordinal })
                    .take(3).map { it.key to it.value },
            )
        }
    }
}

internal fun hasBatchim(s: String) = Insights.hasBatchim(s)

val SLOTS = Insights.SLOTS

fun slotOf(time: String) = Insights.slotOf(time)

/** One pattern cell: dominant group and its weight relative to the busiest cell (0..1). */
data class PatternCell(val group: EmotionGroup?, val amount: Float)

/** 4 slots × 7 weekdays (월..일). Entries with an unknown time (v1) are left out. */
fun pattern(entries: List<Entry>): List<List<PatternCell>> {
    val cells = entries.filter { !it.timeUnknown }.groupBy { slotOf(it.time) to LocalDate.parse(it.date).dayOfWeek.value - 1 }
    val totals = cells.mapValues { (_, l) -> l.flatMap { it.emotions }.filter { it.key.group != null }.sumOf { it.intensity } }
    val max = totals.values.maxOrNull()?.takeIf { it > 0 } ?: 1
    return List(4) { s ->
        List(7) { d ->
            val l = cells[s to d].orEmpty()
            PatternCell(LightMixer.dominantGroup(l.flatMap { it.emotions }), (totals[s to d] ?: 0) / max.toFloat())
        }
    }
}

/** "평일 아침엔 지침, 주말 낮엔 밝음이 많았어요." as (bold, plain) runs; null when nothing to say. */
fun patternSummary(grid: List<List<PatternCell>>): List<Pair<String, Boolean>>? {
    fun busiest(days: IntRange): Pair<Int, EmotionGroup>? = (0..3).flatMap { s -> days.map { d -> s to grid[s][d] } }
        .filter { it.second.group != null }.groupBy { it.first }
        .maxByOrNull { (_, l) -> l.sumOf { it.second.amount.toDouble() } }
        ?.let { (s, l) -> s to l.groupBy { it.second.group!! }.maxBy { (_, c) -> c.sumOf { it.second.amount.toDouble() } }.key }
    val parts = listOfNotNull(busiest(0..4)?.let { "평일 " to it }, busiest(5..6)?.let { "주말 " to it })
    if (parts.isEmpty()) return null
    return buildList {
        parts.forEachIndexed { i, (who, sg) ->
            val name = sg.second.label
            add(who + SLOTS[sg.first] to true)
            val tail = if (i < parts.lastIndex) ", " else "${if (hasBatchim(name)) "이" else "가"} 많았어요."
            add("엔 $name$tail" to false)
        }
    }
}

/** Consecutive recorded days ending today or yesterday; one missed day forgiven (Gating.streak). */
fun streak(dates: Set<LocalDate>, today: LocalDate): Int = Gating.streak(dates, today)

/** Monday of [date]'s week. */
fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

// ---------------------------------------------------------------- 온이 한마디 (shared/library/retro.json)

data class RetroLine(val id: String, val kind: String, val condition: String, val text: String)

/** Values the retro.json conditions/slots refer to. Absent = null (conditions on it are false). */
data class RetroFacts(
    val entries: Int,
    val streak: Int,
    val distinctGroups: Int,
    val topGroup: String?,
    val groupShare: Map<String, Float>,
    val nightShare: Float,
    val unsureShare: Float,
    val topActivity: String?,
    val topPerson: String?,
    val topPlace: String?,
    val period: String,
    val topEmotionName: String?,
    val topGroupName: String?,
    val bestDay: String?,
) {
    companion object {
        fun of(entries: List<Entry>, streak: Int, period: String): RetroFacts {
            val r = MonthReport.of(entries)
            val all = entries.flatMap { it.emotions }
            val unsure = all.count { it.key == EmotionKey.UNSURE }
            fun topTag(kind: TagKind) = entries.flatMap { e -> e.tags.filter { it.kind == kind }.map { it.value } }
                .groupingBy { it }.eachCount().filter { it.value >= 2 }.maxByOrNull { it.value }?.key
            val dom = r.dominant
            val best = entries.groupBy { it.date }.maxByOrNull { (_, l) ->
                l.flatMap { it.emotions }.filter { it.key.group == EmotionGroup.BRIGHT || it.key.group == EmotionGroup.CALM }.sumOf { it.intensity }
            }?.key?.let { LocalDate.parse(it) }
            return RetroFacts(
                entries = entries.size, streak = streak, distinctGroups = r.groups.count { it.second > 0f },
                topGroup = dom?.key ?: if (unsure > 0) "unsure" else null,
                groupShare = r.groups.associate { it.first.key to it.second },
                nightShare = if (entries.isEmpty()) 0f else entries.count { !it.timeUnknown && slotOf(it.time) == 3 } / entries.size.toFloat(),
                unsureShare = if (all.isEmpty()) 0f else unsure / all.size.toFloat(),
                topActivity = topTag(TagKind.ACTIVITY), topPerson = topTag(TagKind.PERSON), topPlace = topTag(TagKind.PLACE),
                period = period, topEmotionName = r.top.firstOrNull()?.first?.label, topGroupName = dom?.label,
                bestDay = best?.let { "${it.monthValue}월 ${it.dayOfMonth}일" },
            )
        }
    }
}

private fun RetroFacts.number(name: String): Float? = when {
    name == "entries" -> entries.toFloat()
    name == "streak" -> streak.toFloat()
    name == "distinctGroups" -> distinctGroups.toFloat()
    name == "nightShare" -> nightShare
    name == "unsureShare" -> unsureShare
    name.startsWith("groupShare(") -> groupShare[name.removePrefix("groupShare(").removeSuffix(")")] ?: 0f
    else -> null
}

private fun RetroFacts.text(name: String): String? = when (name) {
    "topGroup" -> topGroup
    "topActivity" -> topActivity
    "topPerson" -> topPerson
    "topPlace" -> topPlace
    else -> null // trend, brightestPerson: not computed yet → those lines never match
}

/** Evaluates "a>=3 && topGroup==calm"-style conditions. Unknown terms are false. */
fun RetroFacts.matches(condition: String): Boolean = condition == "always" || condition.split("&&").all { term ->
    val m = Regex("""\s*([\w()]+)\s*(>=|<=|==|!=)\s*([\w.]+)\s*""").matchEntire(term) ?: return@all false
    val (name, op, value) = m.destructured
    val n = number(name)
    if (n != null) {
        val v = value.toFloatOrNull() ?: return@all false
        when (op) { ">=" -> n >= v; "<=" -> n <= v; "==" -> n == v; else -> n != v }
    } else {
        val t = text(name)
        when (op) { "==" -> t == value; "!=" -> if (value == "null") t != null else t != value; else -> false }
    }
}

/** Fills {slots} and 조사 markers "(이)", "(으)". null when a needed slot is missing. */
fun RetroFacts.fill(text: String): String? {
    val slots = mapOf(
        "period" to period, "count" to entries.toString(), "streak" to streak.toString(), "topEmotionName" to topEmotionName,
        "topGroupName" to topGroupName, "activity" to topActivity, "person" to topPerson, "place" to topPlace, "bestDay" to bestDay,
    )
    var ok = true
    val filled = Regex("""\{(\w+)\}""").replace(text) { m -> slots[m.groupValues[1]] ?: run { ok = false; "" } }
    if (!ok) return null
    return Regex("""(.)\((이|으)\)""").replace(filled) { m ->
        val prev = m.groupValues[1]
        val keep = hasBatchim(prev) && !(m.groupValues[2] == "으" && prev.last().let { (it - '가') % 28 == 8 })
        prev + if (keep) m.groupValues[2] else ""
    }
}

/** Most specific matching line (most && terms), deterministic per [seed]. */
fun pickRetro(lines: List<RetroLine>, kind: String, facts: RetroFacts, seed: Int): String? {
    val ok = lines.filter { it.kind == kind && facts.matches(it.condition) }.mapNotNull { l -> facts.fill(l.text)?.let { l to it } }
    if (ok.isEmpty()) return null
    val best = ok.maxOf { specificity(it.first.condition) }
    val top = ok.filter { specificity(it.first.condition) == best }
    return top[Math.floorMod(seed, top.size)].second
}

private fun specificity(c: String) = if (c == "always") 0 else c.split("&&").size
