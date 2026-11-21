package com.pass.diary.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.billing.AdBanner
import com.pass.diary.core.billing.AdNative
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.DayLightStrip
import com.pass.diary.core.designsystem.DayMark
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.PhotoPlaceholder
import com.pass.diary.core.designsystem.SegTabs
import com.pass.diary.core.designsystem.TabHeader
import com.pass.diary.core.designsystem.cellColor
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.odCard
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Photo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Serializable data object CalendarRoute
/** [q] = start with this query (e.g. a tag tapped elsewhere). */
@Serializable data class SearchRoute(val q: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(repository: EntryRepository, replyDao: ReplyDao) : ViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val selected = MutableStateFlow(LocalDate.now())

    val monthEntries = month.flatMapLatest { m -> repository.observeRange(m.atDay(1).toString(), m.atEndOfMonth().toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** entryId → reply text. */
    val replies = replyDao.observeAll().map { l -> l.associate { it.entryId to it.text } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setMonth(m: YearMonth) {
        month.value = m
        selected.value = if (m == YearMonth.now()) LocalDate.now() else m.atDay(1)
    }
}

internal fun koDay(d: LocalDate) = "${d.dayOfMonth}일 ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}"

/** The glows of a day's entries in time order (one color repeated for a single entry). */
internal fun dayGlows(entries: List<Entry>): List<Color> =
    entries.sortedBy { it.time }.map { rgb(LightMixer.mix(it.emotions).glow) }

@Composable
fun CalendarScreen(onOpen: (String) -> Unit, onWriteOn: (String) -> Unit, onSearch: () -> Unit, vm: CalendarViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val month by vm.month.collectAsStateWithLifecycle()
    val entries by vm.monthEntries.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MonthHeader(month, vm::setMonth, onSearch)
            SegTabs(listOf("캘린더", "타임라인", "사진"), tab, { tab = it }, Modifier.padding(horizontal = 20.dp))
            when (tab) {
                0 -> MonthTab(vm, entries, onOpen, onWriteOn)
                1 -> TimelineTab(vm, entries, onOpen)
                else -> PhotosTab(entries, onOpen)
            }
        }
        if (tab == 0) AdBanner(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 84.dp))
    }
}

@Composable
private fun MonthHeader(month: YearMonth, setMonth: (YearMonth) -> Unit, onSearch: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = Od.c
    TabHeader(
        "${month.year}년 ${month.monthValue}월",
        titleTrailing = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 4.dp, vertical = 8.dp)) {
                OdIcon(OdIcon.ChevD, size = 20.dp, tint = c.ink2, contentDescription = "달 고르기")
                DropdownMenu(open, { open = false }, containerColor = c.surface) {
                    val now = YearMonth.now()
                    (0L..23L).map { now.minusMonths(it) }.forEach { m ->
                        DropdownMenuItem(
                            { Text("${m.year}년 ${m.monthValue}월", style = OdType.body2, fontWeight = if (m == month) FontWeight.Bold else null) },
                            { setMonth(m); open = false },
                        )
                    }
                }
            }
        },
        right = { IconBtn(OdIcon.Search, onSearch, "검색") },
    )
}

@Composable
private fun MonthTab(vm: CalendarViewModel, entries: List<Entry>, onOpen: (String) -> Unit, onWriteOn: (String) -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val c = Od.c
    val byDate = entries.groupBy { it.date }
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 180.dp).navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            "일월화수목금토".forEachIndexed { i, w ->
                Text(
                    "$w", Modifier.weight(1f), style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold),
                    color = if (i == 0) Color(0xFFD06A5E) else c.ink2, textAlign = TextAlign.Center,
                )
            }
        }
        val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday first
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        Box(Modifier.weight(1f)) {
                            if (day != null) DayCell(day, byDate[day.toString()].orEmpty(), day == selected, day.isAfter(today)) { vm.selected.value = day }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            EmotionGroup.entries.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Dot(g.glowColor, 8.dp)
                    Text(g.label, style = OdType.caption2, color = c.ink2)
                }
            }
        }
        DayCard(selected, byDate[selected.toString()].orEmpty().sortedBy { it.time }, selected.isAfter(today), onOpen, onWriteOn)
    }
}

/** A day lit by its light; several entries = 160° linear gradient in time order (README §2). */
@Composable
private fun DayCell(day: LocalDate, entries: List<Entry>, selected: Boolean, future: Boolean, onClick: () -> Unit) {
    val c = Od.c
    val lights = entries.sortedBy { it.time }.map { cellColor(LightMixer.mix(it.emotions)) }
    val bg = when {
        lights.isNotEmpty() -> lights
        future -> listOf(Color.Transparent)
        else -> listOf(c.surface2)
    }
    val fg = when {
        lights.isNotEmpty() -> if (c.dark) OdColors.Night else OdColors.OnLight
        future -> c.ink3
        else -> c.ink2
    }
    val r = 14.dp
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .drawBehind {
                if (selected) {
                    val o = 3.5.dp.toPx(); val g = 2.dp.toPx()
                    drawRoundRect(c.ink, Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), CornerRadius(r.toPx() + o))
                    drawRoundRect(c.bg, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), CornerRadius(r.toPx() + g))
                }
                val rad = r.toPx()
                if (bg.size == 1) drawRoundRect(bg[0], cornerRadius = CornerRadius(rad))
                else {
                    // CSS 160deg: pointing down, slightly right.
                    val a = 160.0 * PI / 180
                    val dx = sin(a).toFloat(); val dy = (-cos(a)).toFloat()
                    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2
                    val ctr = Offset(size.width / 2, size.height / 2)
                    drawRoundRect(Brush.linearGradient(bg, ctr - Offset(dx, dy) * half, ctr + Offset(dx, dy) * half), cornerRadius = CornerRadius(rad))
                }
            }
            .clip(RoundedCornerShape(r))
            .clickable(onClick = onClick)
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            "${day.dayOfMonth}", style = OdType.caption1.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, lineHeight = 16.sp),
            color = fg,
        )
    }
}

@Composable
private fun DayCard(day: LocalDate, entries: List<Entry>, future: Boolean, onOpen: (String) -> Unit, onWriteOn: (String) -> Unit) {
    val c = Od.c
    Column(Modifier.padding(top = 14.dp).fillMaxWidth().odCard().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(koDay(day), style = OdType.title3, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) Text("기록 ${entries.size}개", style = OdType.caption2, color = c.ink2)
        }
        when {
            entries.isNotEmpty() -> {
                DayLightStrip(entries.map { DayMark(it.time, it.emotions) }, Modifier.padding(top = 12.dp), height = 10.dp, labels = false)
                val e = entries.firstOrNull { it.title.isNotBlank() || it.body.isNotBlank() } ?: entries.first()
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(e.id) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PhotoThumb(entries.flatMap { it.photos }.firstOrNull(), e.id.hashCode(), 44.dp, 10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e.title.ifBlank { e.emotions.joinToString(" · ") { it.key.label }.ifBlank { "기록" } }, style = OdType.body2.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.body.isNotBlank()) Text(e.body.lines().first(), style = OdType.caption2, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            future -> Text("아직 오지 않은 날이에요", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
            else -> {
                Text("이날은 기록이 없어요", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                OdButton("이날 쓰기", { onWriteOn(day.toString()) }, Modifier.padding(top = 12.dp), kind = ButtonKind.Secondary, small = true, icon = OdIcon.Pen)
            }
        }
    }
}

/** Photo with a film-tone placeholder underneath (shows if the file is missing). */
@Composable
internal fun PhotoThumb(photo: Photo?, seed: Int, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(radius))) {
        PhotoPlaceholder(photo?.id?.hashCode() ?: seed, Modifier.fillMaxSize())
        if (photo != null) AsyncImage(File(photo.localPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun TimelineTab(vm: CalendarViewModel, entries: List<Entry>, onOpen: (String) -> Unit) {
    val replies by vm.replies.collectAsStateWithLifecycle()
    val days = entries.groupBy { it.date }.toList().sortedByDescending { it.first }
    val c = Od.c
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (days.isEmpty()) item { Text("이 달엔 아직 기록이 없어요", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 24.dp)) }
        days.forEachIndexed { i, (date, list) ->
            if (i == 2) item(key = "ad") { AdNative() }
            item(key = date) { DayTimelineCard(LocalDate.parse(date), list.sortedBy { it.time }, replies, onOpen) }
        }
        if (days.size == 2) item(key = "ad") { AdNative() }
    }
}

@Composable
private fun DayTimelineCard(day: LocalDate, entries: List<Entry>, replies: Map<String, String>, onOpen: (String) -> Unit) {
    val c = Od.c
    val main = entries.firstOrNull { it.title.isNotBlank() } ?: entries.firstOrNull { it.body.isNotBlank() } ?: entries.last()
    val keys = entries.flatMap { e -> e.emotions.map { it.key } }.distinct()
    val glows = dayGlows(entries).let { if (it.size == 1) it + it else it }
    val replied = entries.lastOrNull { replies[it.id] != null }
    Row(
        Modifier.fillMaxWidth().odCard().clickable { onOpen(main.id) }.height(IntrinsicSize.Min)
            .padding(start = 14.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(glows)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(koDay(day), style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.take(4).forEach { k -> EmotionTag(k) }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(main.title.ifBlank { keys.joinToString(" · ") { it.label } }, style = OdType.title3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (main.body.isNotBlank()) Text(
                        main.body.replace('\n', ' '), style = OdType.body2, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                entries.flatMap { it.photos }.firstOrNull()?.let { PhotoThumb(it, 0, 56.dp, 10.dp) }
            }
            if (replied != null) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val light = LightMixer.mix(replied.emotions)
                    Oni(light, OniExpression.forGroup(LightMixer.dominantGroup(replied.emotions)).takeIf { !light.off } ?: OniExpression.DEFAULT, Modifier.size(26.dp), animate = false)
                    Text(replies[replied.id].orEmpty(), style = OdType.caption2, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun EmotionTag(k: EmotionKey) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Dot(k.group?.glowColor ?: OdColors.OffGlow, 8.dp)
        Text(k.label, style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = Od.c.ink)
    }
}

@Composable
private fun PhotosTab(entries: List<Entry>, onOpen: (String) -> Unit) {
    val photos = entries.flatMap { e -> e.photos.map { it to e.id } }
    if (photos.isEmpty()) {
        Text("이 달엔 사진이 없어요", style = OdType.body2, color = Od.c.ink2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 40.dp))
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(photos, key = { it.first.id }) { (p, id) ->
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpen(id) }) {
                PhotoThumb(p, 0, 400.dp, 12.dp, Modifier.fillMaxSize())
            }
        }
    }
}
