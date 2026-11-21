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
import androidx.compose.foundation.layout.wrapContentSize
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
    val relations: List<Relation>,
    val grid: List<List<PatternCell>>,
    val summary: List<Pair<String, Boolean>>?,
    val streak: Int,
    val retro: String,
    val light: com.pass.diary.core.light.Light,
)

/** Every entry ever: streaks and the year mosaic need more than one period. ponytail: in-memory; page by year if diaries reach 10k+. */
internal fun EntryRepository.all() = observeRange("0000-01-01", "9999-12-31")

@HiltViewModel
class ReportViewModel @Inject constructor(@ApplicationContext context: Context, repository: EntryRepository) : ViewModel() {
    /** 0 = 주간, 1 = 월간. */
    val tab = MutableStateFlow(1)
    val offset = MutableStateFlow(0)

    private val retro: List<RetroLine> = runCatching {
        context.assets.open("library/retro.json").bufferedReader().use { Json { ignoreUnknownKeys = true }.decodeFromString<List<RetroLine>>(it.readText()) }
    }.getOrDefault(emptyList())

    val ui = combine(repository.all(), tab, offset) { all, t, off -> build(all, t, off) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(by: Int) { offset.value = (offset.value + by).coerceAtMost(0) }
    fun setTab(t: Int) { if (t != tab.value) { tab.value = t; offset.value = 0 } }

    private fun build(all: List<Entry>, t: Int, off: Int): ReportUi {
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
        return ReportUi(
            label = if (start.monthValue == shownEnd.monthValue) "${start.monthValue}월 ${start.dayOfMonth}일 – ${shownEnd.dayOfMonth}일"
            else "${start.monthValue}월 ${start.dayOfMonth}일 – ${shownEnd.monthValue}월 ${shownEnd.dayOfMonth}일",
            canNext = off < 0,
            report = MonthReport.of(inPeriod),
            relations = relations(inPeriod),
            grid = grid,
            summary = patternSummary(grid),
            streak = streak,
            retro = pickRetro(retro, if (t == 0) "weekly" else "monthly", facts, start.hashCode())
                ?: "$period 이야기, 하나하나 다 기억하고 있을게.",
            light = LightMixer.mix(inPeriod.flatMap { it.emotions }),
        )
    }
}

@Composable
fun ReportScreen(onLetters: () -> Unit, onYear: () -> Unit, vm: ReportViewModel = hiltViewModel()) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = Od.c
    val dom = ui?.report?.dominant
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
            if (r.entries > 0 && wide) {
                // Tablets: the cards in two columns (charts left, patterns right).
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Distribution(r); Top3(r); Streak(u.streak) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Relations(u.relations); Pattern(u.grid, u.summary) }
                }
            } else if (r.entries > 0) {
                Spacer(Modifier.height(14.dp))
                Distribution(r)
                Spacer(Modifier.height(12.dp))
                Top3(r)
                Spacer(Modifier.height(12.dp))
                Relations(u.relations)
                Spacer(Modifier.height(12.dp))
                Pattern(u.grid, u.summary)
            }
            if (!wide || r.entries == 0) {
                Spacer(Modifier.height(12.dp))
                Streak(u.streak)
            }
            AdBanner(Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(Od.layout.bottomBar + 42.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

@Composable
private fun Relations(rel: List<Relation>) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth()) {
        CardHeader("무엇을 하면 빛이 바뀔까", "평소보다")
        Spacer(Modifier.height(10.dp))
        if (rel.isEmpty()) Text("태그를 붙여 쓰다 보면, 어떤 날 빛이 바뀌는지 알려 줄게요.", style = OdType.body2, color = c.ink2)
        val max = rel.maxOfOrNull { it.lift }?.coerceAtLeast(1) ?: 1
        rel.forEachIndexed { i, r ->
            Column(Modifier.padding(top = if (i == 0) 0.dp else 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(r.label) }
                            withStyle(SpanStyle(color = c.ink2)) { append(" → ") }
                            append(r.group.label)
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
        Text("태그가 3번 이상 나온 활동·사람만 계산해요.", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 14.dp))
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
private fun Streak(days: Int) {
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
                // ponytail: palette rewards aren't built yet; the hint names the 14-day one from the 시안.
                Text(if (days < 14) "14일이 되면 새 빛 팔레트 「노을」을 받아요" else "빛 팔레트 「노을」을 받았어요", style = OdType.caption2, color = Od.c.ink2)
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

private val EmotionGroup.colorName get() = when (this) {
    EmotionGroup.BRIGHT -> "환한 노랑"
    EmotionGroup.CALM -> "평온한 초록"
    EmotionGroup.FLUTTER -> "설레는 분홍"
    EmotionGroup.LOW -> "잔잔한 파랑"
    EmotionGroup.WORN -> "고단한 보라"
}

@Composable
fun YearLightScreen(onBack: () -> Unit, onPremium: () -> Unit, vm: YearLightViewModel = hiltViewModel()) {
    val c = Od.c
    val ui by vm.ui.collectAsStateWithLifecycle()
    val premium = Od.premium
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.bg).ambient(ui?.dominant?.glowColor ?: OdColors.Dusk, 1.1f, 360.dp, 460.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            com.pass.diary.core.designsystem.OdTopBar(
                title = "올해의 빛",
                left = { IconBtn(OdIcon.Back, onBack, "뒤로") },
                right = { com.pass.diary.core.designsystem.PremiumBadge(modifier = Modifier.padding(end = 10.dp)) },
            )
            val u = ui
            if (u != null) StoryCard(u, Modifier.padding(top = 14.dp))
            Text("365일의 빛을 한 장에 담았어요", style = OdType.title2, modifier = Modifier.padding(top = 22.dp))
            Text(
                "인스타그램 스토리 비율(9:16)로 저장돼요.\n일기 내용은 들어가지 않아요.", style = OdType.caption1, color = c.ink2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = 30.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val name = "onee_year_${ui?.year ?: ""}"
            val export = { block: suspend (android.graphics.Bitmap) -> Unit ->
                if (!premium) onPremium() else if (!busy && ui != null) {
                    busy = true
                    scope.launch {
                        runCatching { block(layer.toImageBitmap().asAndroidBitmap().toStory()) }.onFailure {
                            android.util.Log.w("YearLight", "export failed", it)
                            android.widget.Toast.makeText(ctx, "이미지를 만들지 못했어요. 다시 해 주세요", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        busy = false
                    }
                }
            }
            com.pass.diary.core.designsystem.OdButton("저장", {
                export { bmp ->
                    if (saveToGallery(ctx, bmp, name)) android.widget.Toast.makeText(ctx, "사진첩 「온이 일기」에 저장했어요", android.widget.Toast.LENGTH_SHORT).show()
                    else shareImage(ctx, bmp, name)
                }
            }, Modifier.weight(1f), kind = com.pass.diary.core.designsystem.ButtonKind.Secondary, icon = if (premium) OdIcon.Download else OdIcon.Lock)
            com.pass.diary.core.designsystem.OdButton("스토리에 공유", { export { shareImage(ctx, it, name) } }, Modifier.weight(1.4f), icon = OdIcon.Share)
        }
        // The shared image: the same card laid out at 1080×1920 px (density scaled so 236×420 dp fills it), recorded into
        // [layer] and never drawn on screen. Rendering at full size keeps the text sharp instead of upscaling the preview.
        val u = ui
        if (u != null && premium) Box(Modifier.size(0.dp)) {
            CompositionLocalProvider(LocalDensity provides Density(STORY_H / 420f, 1f)) {
                StoryCard(
                    u, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).drawWithContent { layer.record { this@drawWithContent.drawContent() } },
                    export = true,
                )
            }
        }
    }
}

@Composable
private fun StoryCard(u: YearUi, modifier: Modifier = Modifier, export: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    val night = Color(0xFF2A2448)
    val glow = u.dominant?.glowColor ?: OdColors.BrandGlow
    Box(
        modifier.size(236.dp, 420.dp)
            .let { if (export) it else it.floating(shape).clip(shape) }
            .background(Brush.verticalGradient(listOf(night, OdColors.Night)))
            .drawBehind {
                // Low glow behind the mini Oni (시안: radial at y≈200..500 of the card).
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.83f), radius = size.width * 0.9f), size.width * 0.9f, Offset(size.width / 2, size.height * 0.83f))
            }
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Column {
            val ink = Color(0xFFF4EFE8)
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
                    val col = if (l == null || l.off) Color.White.copy(alpha = 0.07f)
                    else lerp(night, com.pass.diary.core.designsystem.rgb(l.glow), 0.6f + 0.4f * l.strength)
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
        // The shared image carries the app name (the in-app preview doesn't need it).
        if (export) Text(
            stringResourceAppName(), style = OdType.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = Color(0xFFF4EFE8).copy(alpha = 0.55f), modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** "온이 일기" (app_name lives in :app; the feature reads it by name so the name stays in one place). */
@Composable
private fun stringResourceAppName(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember { ctx.applicationInfo.loadLabel(ctx.packageManager).toString() }
}

@Composable
private fun Modifier.floating(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val m = dropShadow(shape, Shadow(radius = 50.dp, color = Color(0x591C1830), offset = DpOffset(0.dp, 20.dp)))
    return if (Od.c.dark) m.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else m
}
