package com.pass.diary.feature.report

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.billing.AdBanner
import com.pass.diary.core.designsystem.CardHeader
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.LightOrb
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.SegTabs
import com.pass.diary.core.designsystem.TabHeader
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.lightColor
import com.pass.diary.core.designsystem.odCard
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import com.pass.diary.core.billing.CardTheme
import com.pass.diary.core.billing.StoryShare
import com.pass.diary.core.billing.StoryShareDialog
import com.pass.diary.core.billing.NudgeReason
import com.pass.diary.core.billing.PremiumNudgeSheet
import com.pass.diary.core.data.Insights
import com.pass.diary.core.model.Gating
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import com.pass.diary.core.designsystem.AiBadge
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Serializable data object ReportRoute
@Serializable data object YearLightRoute
@Serializable data object LettersRoute

data class ReportUi(
    val label: String,
    val canNext: Boolean,
    val report: MonthReport,
    val causes: List<Insights.Cause>,
    /** E13: stored tags first; [ReportViewModel] adds what the tag dictionary finds once the AI answers. */
    val keywords: List<Pair<Tag, Int>>,
    val grid: List<List<PatternCell>>,
    val summary: List<Pair<String, Boolean>>?,
    val streak: Int,
    val retro: String,
    val light: com.pass.diary.core.light.Light,
    val start: LocalDate,
    /** Month tab: each day's light (null = no record), for the 빛 캘린더 card. */
    val days: List<com.pass.diary.core.light.Light?>,
    /** E7 이번 달의 한 줄 (month tab only), null until / unless the model picks one. */
    val line: String? = null,
    /** Collected days / best streak, for the 빛 수집 hint. */
    val progress: Gating.Progress = Gating.Progress(),
)

/** Every entry ever: streaks and the year mosaic need more than one period. ponytail: in-memory; page by year if diaries reach 10k+. */
internal fun EntryRepository.all() = observeRange("0000-01-01", "9999-12-31")

@HiltViewModel
class ReportViewModel @Inject constructor(
    @ApplicationContext context: Context,
    repository: EntryRepository,
    private val ai: ReportAi,
    val nudges: com.pass.diary.core.billing.NudgeGate,
) : ViewModel() {
    /** 0 = 주간, 1 = 월간. */
    val tab = MutableStateFlow(1)
    val offset = MutableStateFlow(0)

    private val retro: List<RetroLine> = runCatching {
        context.assets.open("library/retro.json").bufferedReader().use { Json { ignoreUnknownKeys = true }.decodeFromString<List<RetroLine>>(it.readText()) }
    }.getOrDefault(emptyList())

    private val lineMemo = AiMemo<String?>()
    private val tagMemo = AiMemo<List<List<Tag>>>()

    /** The statistics at once, then again with the model's picks (E7 line, E13 extracted tags) when they're ready. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui = combine(repository.all(), tab, offset) { all, t, off -> Triple(all, t, off) }
        .transformLatest { (all, t, off) ->
            val (base, inPeriod) = build(all, t, off)
            emit(base)
            if (inPeriod.isEmpty()) return@transformLatest
            val tags = tagMemo.get(inPeriod) { inPeriod.map { e -> e.tags + runCatching { ai.tags(e.body) }.getOrDefault(emptyList()) } }
            val line = if (t == 1) lineMemo.get(inPeriod) { runCatching { ai.centralSentence(inPeriod.map { it.body }) }.getOrNull() } else null
            emit(base.copy(keywords = Insights.keywords(tags), line = line))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(by: Int) { offset.value = (offset.value + by).coerceAtMost(0) }
    fun setTab(t: Int) { if (t != tab.value) { tab.value = t; offset.value = 0 } }

    private fun build(all: List<Entry>, t: Int, off: Int): Pair<ReportUi, List<Entry>> {
        val today = LocalDate.now()
        val (start, end) = if (t == 0) weekStart(today).plusWeeks(off.toLong()).let { it to it.plusDays(6) }
        else YearMonth.from(today).plusMonths(off.toLong()).let { it.atDay(1) to it.atEndOfMonth() }
        val shownEnd = if (end.isAfter(today)) today else end
        val inPeriod = all.filter { LocalDate.parse(it.date).let { d -> !d.isBefore(start) && !d.isAfter(end) } }
        val streak = streak(all.map { LocalDate.parse(it.date) }.toSet(), today)
        val period = when {
            t == 0 && off == 0 -> "이번 주"
            t == 0 && off == -1 -> "지난주"
            t == 0 -> "그 주"
            off == 0 -> "이번 달"
            else -> "${start.monthValue}월"
        }
        val facts = RetroFacts.of(inPeriod, streak, period)
        val grid = pattern(inPeriod)
        val byDate = inPeriod.groupBy { it.date }
        return ReportUi(
            label = if (start.monthValue == shownEnd.monthValue) "${start.monthValue}월 ${start.dayOfMonth}일 – ${shownEnd.dayOfMonth}일"
            else "${start.monthValue}월 ${start.dayOfMonth}일 – ${shownEnd.monthValue}월 ${shownEnd.dayOfMonth}일",
            canNext = off < 0,
            report = MonthReport.of(inPeriod),
            causes = Insights.causes(inPeriod),
            keywords = Insights.keywords(inPeriod.map { it.tags }),
            grid = grid,
            summary = patternSummary(grid),
            streak = streak,
            retro = pickRetro(retro, if (t == 0) "weekly" else "monthly", facts, start.hashCode())
                ?: "$period 이야기, 하나하나 다 기억하고 있을게.",
            light = LightMixer.mix(inPeriod.flatMap { it.emotions }),
            start = start,
            days = if (t == 0) emptyList() else (0 until start.lengthOfMonth()).map { i -> byDate[start.plusDays(i.toLong()).toString()]?.let { l -> LightMixer.mixDay(l.map { it.emotions }) } },
            progress = Gating.Progress.of(all.map { it.date }),
        ) to inPeriod
    }
}

@Composable
fun ReportScreen(
    onLetters: () -> Unit,
    onYear: () -> Unit,
    onWeeklyLetter: (offset: Int) -> Unit = {},
    onPremium: () -> Unit = {},
    vm: ReportViewModel = hiltViewModel(),
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val offset by vm.offset.collectAsStateWithLifecycle()
    val c = Od.c
    val dom = ui?.report?.dominant
    var sharing by remember { mutableStateOf(false) }
    // AI_GROWTH §5 ④ 연말 올해의 빛: once a year in December, at most one suggestion a day.
    val premium = Od.premium
    val year = LocalDate.now().year
    var yearNudge by remember { mutableStateOf(!premium && LocalDate.now().monthValue == 12 && vm.nudges.canShow("year_$year")) }
    androidx.compose.runtime.LaunchedEffect(yearNudge) { if (yearNudge) vm.nudges.shown("year_$year") }
    Column(
        Modifier.fillMaxSize().background(c.bg).ambient(dom?.glowColor ?: OdColors.Dusk, 0.7f, 150.dp, 380.dp)
            .verticalScroll(rememberScrollState()).statusBarsPadding(),
    ) {
        TabHeader("리포트") { IconBtn(OdIcon.Mail, onLetters, "온이 편지함") }
        val wide = Od.layout.rail
        Column(Modifier.readableWidth(1040.dp).padding(horizontal = 20.dp)) {
            SegTabs(listOf("주간", "월간", "올해의 빛"), tab, { if (it == 2) onYear() else vm.setTab(it) }, Modifier.readableWidth(480.dp))
            val u = ui ?: return@Column
            Row(Modifier.readableWidth(480.dp).padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(OdIcon.ChevL, { vm.shift(-1) }, "이전", size = 20.dp)
                Text(u.label, style = OdType.title3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconBtn(OdIcon.ChevR, { if (u.canNext) vm.shift(1) }, "다음", size = 20.dp, tint = c.ink2, modifier = Modifier.alpha(if (u.canNext) 1f else 0.35f))
            }
            val r = u.report
            Row(Modifier.readableWidth(560.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OniFigure(if (r.entries == 0) LightMixer.OFF else u.light, if (r.entries == 0) OniExpression.SLEEP else OniExpression.forGroup(dom ?: EmotionGroup.CALM), 96.dp)
                Column(Modifier.weight(1f).odCard(RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 12.dp)) {
                    Text(if (r.entries == 0) "이 기간엔 아직 켜진 빛이 없어. 오늘 하나 켜 볼까?" else u.retro, style = OdType.say.copy(fontSize = 15.sp, lineHeight = 23.sp))
                    Text("온이의 한마디", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            }
            val month = tab == 1
            // The headline cards: 주간 → 온이 편지, 월간 → 이번 달의 한 줄 + 빛 캘린더 공유.
            val lead: @Composable () -> Unit = {
                if (!month) LetterEntry(offset) { onWeeklyLetter(offset) }
                else {
                    u.line?.let { MonthLine(it) }
                    MonthCalendar(u) { sharing = true }
                }
            }
            if (r.entries > 0 && wide) {
                // Tablets: the cards in two columns (charts left, patterns right).
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { lead(); Distribution(r); Top3(r); Streak(u.streak, u.progress) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Causes(u.causes); Keywords(u.keywords); Pattern(u.grid, u.summary) }
                }
            } else if (r.entries > 0) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    lead(); Distribution(r); Top3(r); Causes(u.causes); Keywords(u.keywords); Pattern(u.grid, u.summary)
                }
            }
            if (!wide || r.entries == 0) {
                Spacer(Modifier.height(12.dp))
                Streak(u.streak, u.progress)
            }
            AdBanner(Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(Od.layout.bottomBar + 42.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    val u = ui
    if (sharing && u != null) StoryShareDialog(
        "${u.start.monthValue}월 빛 캘린더", "onee_month_${u.start.toString().take(7)}", dom?.glowColor ?: OdColors.BrandGlow,
        { sharing = false }, onPremium, "한 달의 빛을 한 장에 담았어요",
    ) { theme -> CalendarCard(u, theme) }
    if (yearNudge) PremiumNudgeSheet(NudgeReason.YEAR_END, vm.nudges, onPremium = { yearNudge = false; onPremium() }, onDismiss = { yearNudge = false })
}

/** 주간: the letter for this week (E10). */
@Composable
private fun LetterEntry(offset: Int, onOpen: () -> Unit) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.pass.diary.core.designsystem.Oni(null, OniExpression.LETTER, Modifier.size(48.dp), animate = false)
            Column(Modifier.weight(1f)) {
                Text(if (offset == 0) "이번 주 온이 편지" else "그 주의 온이 편지", style = OdType.title3)
                Text("빛·자주 느낀 감정·이번 주의 한 줄을 담았어요", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 2.dp))
            }
            OdIcon(OdIcon.ChevR, size = 18.dp, tint = c.ink3)
        }
    }
}

/** E7: the month's most typical sentence, picked by the embedding model. Stays in the app (never on a card). */
@Composable
private fun MonthLine(line: String) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이번 달의 한 줄", style = OdType.title3, modifier = Modifier.weight(1f))
            AiBadge("AI가 골랐어요")
        }
        Text("“$line”", style = OdType.diary.copy(fontSize = 16.sp, lineHeight = 28.sp), modifier = Modifier.padding(top = 10.dp))
        Text("한 달 동안 쓴 문장 중 이달을 가장 닮은 문장이에요", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
    }
}

private const val CAL_DAYS = "월화수목금토일"

/** 월간 빛 캘린더 (in-app) + 공유 button. */
@Composable
private fun MonthCalendar(u: ReportUi, onShare: () -> Unit) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${u.start.monthValue}월 빛 캘린더", style = OdType.title3, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onShare).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OdIcon(OdIcon.Share, size = 16.dp)
                Text("카드로 공유", style = OdType.buttonSm)
            }
        }
        MonthGrid(u.start, u.days, Modifier.padding(top = 12.dp), c.surface2, c.ink2, 5.dp, aspect = 1.8f)
    }
}

@Composable
private fun MonthGrid(start: LocalDate, days: List<com.pass.diary.core.light.Light?>, modifier: Modifier, off: Color, label: Color, gap: androidx.compose.ui.unit.Dp, aspect: Float = 1f) {
    val lead = start.dayOfWeek.value - 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            CAL_DAYS.forEach { Text("$it", style = OdType.caption2.copy(fontSize = 10.sp), color = label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        (0 until (lead + days.size + 6) / 7).forEach { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                (0..6).forEach { d ->
                    val i = w * 7 + d - lead
                    val l = days.getOrNull(i)
                    Box(
                        Modifier.weight(1f).aspectRatio(aspect).clip(RoundedCornerShape(30)).background(
                            when {
                                i !in days.indices -> Color.Transparent
                                l == null || l.off -> off
                                else -> com.pass.diary.core.designsystem.rgb(l.glow)
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(u: ReportUi, theme: CardTheme) {
    val ink = theme.ink
    Column {
        Text("${u.start.year}년 ${u.start.monthValue}월 · 빛 캘린더", style = OdType.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = 0.7f))
        val name = u.report.dominant?.colorName
        Text(
            if (name == null) "이번 달 빛을\n모으는 중이에요" else "${u.start.monthValue}월의 나는\n${name}${if (Insights.hasBatchim(name)) "이었어요" else "였어요"}",
            style = OdType.title1.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold), color = ink, modifier = Modifier.padding(top = 4.dp),
        )
        MonthGrid(u.start, u.days, Modifier.padding(top = 18.dp), theme.offCell, ink.copy(alpha = 0.55f), 4.dp)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.pass.diary.core.designsystem.Oni(u.light.takeUnless { u.report.entries == 0 }, OniExpression.forGroup(u.report.dominant ?: EmotionGroup.CALM), Modifier.size(46.dp), animate = false)
            Text("빛을 켠 날 ${u.days.count { it != null }}일", style = OdType.caption2.copy(fontSize = 11.5.sp), color = ink.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun Distribution(r: MonthReport) {
    val shown = r.groups.filter { it.second > 0f }.sortedByDescending { it.second }
    // Rounded percents that still add up to 100.
    val pct = shown.map { (it.second * 100).toInt() }.toMutableList()
    if (pct.isNotEmpty()) pct[0] += 100 - pct.sum()
    OdCard(Modifier.fillMaxWidth()) {
        CardHeader("빛 분포", "기록 ${r.entries}개")
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEach { (g, s) -> Box(Modifier.weight(s).fillMaxHeight().background(g.glowColor)) }
        }
        Column(Modifier.padding(top = 12.dp)) {
            shown.zip(pct).chunked(2).forEach { row ->
                Row {
                    row.forEach { (gs, p) ->
                        Row(Modifier.weight(1f).height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dot(gs.first.glowColor)
                            Text(gs.first.label, style = OdType.body2, modifier = Modifier.weight(1f))
                            Text("$p%", style = OdType.body2.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Top3(r: MonthReport) {
    OdCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
        CardHeader("자주 느낀 감정 TOP 3")
        Spacer(Modifier.height(10.dp))
        r.top.forEachIndexed { i, (k, n) ->
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${i + 1}", style = OdType.title3, color = Od.c.ink2, modifier = Modifier.width(16.dp))
                LightOrb(k.group?.lightColor ?: OdColors.OffLight, k.group?.glowColor ?: OdColors.OffGlow, 32.dp)
                Text(k.label, style = OdType.body1.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${n}번", style = OdType.caption1, color = Od.c.ink2)
            }
        }
    }
}

/** E8 감정 원인 찾기: tags · weather · weekday · time slot that come with a light more often than usual. */
@Composable
private fun Causes(rel: List<Insights.Cause>) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth()) {
        CardHeader("감정 원인 찾기", "평소보다")
        Spacer(Modifier.height(10.dp))
        if (rel.isEmpty()) Text("기록이 조금 더 쌓이면, 어떤 날 빛이 바뀌는지 알려 줄게요.", style = OdType.body2, color = c.ink2)
        val max = rel.maxOfOrNull { it.lift }?.coerceAtLeast(1) ?: 1
        rel.forEachIndexed { i, r ->
            Column(Modifier.padding(top = if (i == 0) 0.dp else 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(r.label) }
                            withStyle(SpanStyle(color = c.ink2)) { append(" → ") }
                            append(r.group.label)
                            withStyle(SpanStyle(color = c.ink2)) { append("  ${r.days}일") }
                        },
                        style = OdType.body2, modifier = Modifier.weight(1f),
                    )
                    Text("+${r.lift}%", style = OdType.title3)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2)) {
                    Box(Modifier.fillMaxWidth(r.lift / max.toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(r.group.glowColor))
                }
            }
        }
        Text(
            "${Insights.CAUSE_MIN_DAYS}일 이상 함께 나온 태그·날씨·요일·시간대만 봐요. 원인이라기보다 자주 함께 나타난 흐름이에요.",
            style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** E13 키워드 구름: people · places · activities, bigger = more entries. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Keywords(words: List<Pair<Tag, Int>>) {
    if (words.isEmpty()) return
    val c = Od.c
    val max = words.maxOf { it.second }
    OdCard(Modifier.fillMaxWidth()) {
        CardHeader("자주 나온 이야기", "사람 · 장소 · 활동")
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { (t, n) ->
                val w = n / max.toFloat()
                val dot = when (t.kind) { TagKind.PERSON -> EmotionGroup.FLUTTER; TagKind.PLACE -> EmotionGroup.CALM; TagKind.ACTIVITY -> EmotionGroup.BRIGHT; TagKind.FREE -> EmotionGroup.LOW }.glowColor
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.surface2).padding(horizontal = 12.dp + 4.dp * w, vertical = 6.dp + 3.dp * w),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Dot(dot, 7.dp)
                    Text(t.value, style = OdType.body2.copy(fontSize = (13 + 5 * w).sp, fontWeight = if (w > 0.6f) FontWeight.Bold else FontWeight.Medium))
                    Text("$n", style = OdType.caption2, color = c.ink2)
                }
            }
        }
    }
}

@Composable
private fun Pattern(grid: List<List<PatternCell>>, summary: List<Pair<String, Boolean>>?) {
    val c = Od.c
    val from = if (c.dark) Color(0xFF2C2648) else Color.White
    OdCard(Modifier.fillMaxWidth()) {
        CardHeader("요일 · 시간대 패턴")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(44.dp))
            "월화수목금토일".forEach { Text("$it", style = OdType.caption2.copy(fontSize = 11.sp), color = c.ink2, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        grid.forEachIndexed { s, row ->
            Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(SLOTS[s], style = OdType.caption2.copy(fontSize = 11.sp), color = c.ink2, modifier = Modifier.width(39.dp))
                row.forEach { cell ->
                    // Opaque mix (README §2: no translucency, so yellow doesn't turn brown on night).
                    val col = cell.group?.let { lerp(from, it.glowColor, 0.35f + 0.65f * cell.amount) } ?: c.surface2
                    Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(7.dp)).background(col))
                }
            }
        }
        if (summary != null) Text(
            buildAnnotatedString { summary.forEach { (t, b) -> if (b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) } else append(t) } },
            style = OdType.body2, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun Streak(days: Int, progress: Gating.Progress) {
    OdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                buildAnnotatedString {
                    append("$days")
                    withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)) { append("일") }
                },
                style = OdType.display.copy(fontSize = 30.sp, letterSpacing = (-1).sp),
            )
            Column(Modifier.weight(1f)) {
                Text(if (days > 0) "연속으로 기록 중이에요" else "오늘 기록하면 연속 기록이 시작돼요", style = OdType.title3)
                // 빛 수집: the next palette to earn (Gating.REWARDS). A missed day now and then doesn't reset it.
                val next = Gating.REWARDS.firstOrNull { !it.reached(progress) }
                Text(
                    if (next == null) "모을 수 있는 빛 팔레트를 다 모았어요" else "${next.condition}이면 빛 팔레트 「${next.palette.label}」을 받아요 · ${next.progress(progress)}/${next.need}",
                    style = OdType.caption2, color = Od.c.ink2,
                )
                Text("하루쯤 빠져도 이어져요 (일주일에 한 번)", style = OdType.caption2, color = Od.c.ink3)
            }
        }
    }
}

// ---------------------------------------------------------------- 올해의 빛

data class YearUi(val year: Int, val cells: List<com.pass.diary.core.light.Light?>, val dominant: EmotionGroup?, val days: Int, val brightestMonth: Int?)

@HiltViewModel
class YearLightViewModel @Inject constructor(repository: EntryRepository) : ViewModel() {
    val ui = repository.all().combine(MutableStateFlow(LocalDate.now().year)) { all, y ->
        val first = LocalDate.of(y, 1, 1)
        val byDate = all.filter { it.date.startsWith("$y-") }.groupBy { it.date }
        val cells = (0 until first.lengthOfYear()).map { i -> byDate[first.plusDays(i.toLong()).toString()]?.let { l -> LightMixer.mixDay(l.map { it.emotions }) } }
        val emotions = byDate.values.flatten().flatMap { it.emotions }
        YearUi(
            year = y, cells = cells, dominant = LightMixer.dominantGroup(emotions), days = byDate.size,
            brightestMonth = byDate.values.flatten().groupBy { it.date.substring(5, 7).toInt() }
                .mapValues { (_, l) -> l.flatMap { it.emotions }.filter { it.key.group == EmotionGroup.BRIGHT }.sumOf { it.intensity } }
                .filter { it.value > 0 }.maxByOrNull { it.value }?.key,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun YearLightScreen(onBack: () -> Unit, onPremium: () -> Unit, vm: YearLightViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui
    StoryShare(
        title = "올해의 빛", fileName = "onee_year_${u?.year ?: ""}", glow = u?.dominant?.glowColor ?: OdColors.BrandGlow,
        onClose = onBack, onPremium = onPremium, headline = "365일의 빛을 한 장에 담았어요", premiumOnly = true,
        topRight = { com.pass.diary.core.designsystem.PremiumBadge(modifier = Modifier.padding(end = 10.dp)) },
    ) { theme -> if (u != null) YearCard(u, theme) }
}

@Composable
private fun YearCard(u: YearUi, theme: CardTheme) {
    val ink = theme.ink
    Column {
        Text("${u.year} · 올해의 빛", style = OdType.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = 0.7f))
        val name = u.dominant?.colorName
        Text(
            if (name == null) "올해의 빛을\n모으는 중이에요" else "올해 나는\n${name}${if ((name.last() - '가') % 28 != 0) "이었어요" else "였어요"}",
            style = OdType.title1.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold), color = ink, modifier = Modifier.padding(top = 4.dp),
        )
        Canvas(Modifier.padding(top = 16.dp).fillMaxWidth().height(200.dp)) {
            val cols = 19
            val rows = (u.cells.size + cols - 1) / cols
            val gap = 2.dp.toPx()
            val cw = (size.width - gap * (cols - 1)) / cols
            val ch = (size.height - gap * (rows - 1)) / rows
            u.cells.forEachIndexed { i, l ->
                val col = if (l == null || l.off) theme.offCell
                else lerp(theme.top, com.pass.diary.core.designsystem.rgb(l.glow), 0.6f + 0.4f * l.strength)
                drawRoundRect(col, Offset((i % cols) * (cw + gap), (i / cols) * (ch + gap)), Size(cw, ch), CornerRadius(2.dp.toPx()))
            }
        }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.pass.diary.core.designsystem.Oni(
                u.dominant?.let { LightMixer.mix(listOf(com.pass.diary.core.model.EntryEmotion(it.emotions.first(), 5))) },
                OniExpression.forGroup(u.dominant ?: EmotionGroup.CALM), Modifier.size(46.dp), animate = false,
            )
            Text(
                "기록한 날 ${u.days}일" + (u.brightestMonth?.let { "\n가장 밝았던 달 ${it}월" } ?: ""),
                style = OdType.caption2.copy(fontSize = 11.5.sp), color = ink.copy(alpha = 0.85f),
            )
        }
    }
}
