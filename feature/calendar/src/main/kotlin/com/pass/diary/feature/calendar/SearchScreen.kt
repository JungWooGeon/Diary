package com.pass.diary.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.LitChip
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.PaneDivider
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.readableWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import com.pass.diary.core.designsystem.OdChip
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.navigation.toRoute
import javax.inject.Inject

data class SearchFilter(val group: EmotionGroup? = null, val tag: String? = null, val months: Int? = 3, val newestFirst: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: EntryRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    replyDao: com.pass.diary.core.data.db.ReplyDao,
    insights: com.pass.diary.core.data.DiaryInsights,
) : ViewModel() {
    val query = MutableStateFlow(runCatching { savedStateHandle.toRoute<SearchRoute>().q }.getOrNull().orEmpty())
    val filter = MutableStateFlow(SearchFilter())

    private fun SearchFilter.keeps(e: Entry): Boolean {
        val since = months?.let { LocalDate.now().minusMonths(it.toLong()).toString() }
        return (group == null || e.emotions.any { it.key.group == group }) && (tag == null || e.tags.any { it.value == tag }) && (since == null || e.date >= since)
    }

    val results = combine(query.debounce(200).flatMapLatest { repository.search(it, null) }, filter) { list, f ->
        list.filter { f.keeps(it) }.let { if (f.newestFirst) it else it.reversed() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** E5 "비슷한 내용": by meaning + shared words (best first), minus what the word search already found. */
    val similar = combine(
        query.debounce(450).mapLatest { q -> if (q.isBlank()) emptyList() else runCatching { insights.searchByMeaning(q) }.getOrDefault(emptyList()).mapNotNull { repository.get(it) } },
        results, filter,
    ) { found, literal, f -> literal.map { it.id }.toSet().let { have -> found.filter { it.id !in have && !it.deleted && f.keeps(it) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = repository.search("", null).map { l -> l.flatMap { e -> e.tags.map { it.value } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(f: (SearchFilter) -> SearchFilter) { filter.value = f(filter.value) }

    /** entryId → reply text, for the tablet detail pane. */
    val replies = replyDao.observeAll().map { l -> l.associate { it.entryId to it.text } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onRead: (String) -> Unit = onOpen, vm: SearchViewModel = hiltViewModel()) {
    val c = Od.c
    val q by vm.query.collectAsStateWithLifecycle()
    val f by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val similar by vm.similar.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val pane = Od.layout.twoPane
    // Tablets / landscape: results on the left, the chosen one read in full on the right (first result to start with).
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = (results + similar).firstOrNull { it.id == picked } ?: results.firstOrNull() ?: similar.firstOrNull()
    Row(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxHeight().then(if (pane) Modifier else Modifier.readableWidth(720.dp)).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(OdIcon.Back, onBack, "뒤로")
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OdIcon(OdIcon.Search, size = 19.dp)
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("단어나 태그로 찾아요", style = OdType.body1, color = c.ink3)
                    BasicTextField(
                        q, { vm.query.value = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = OdType.body1.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                    )
                }
                if (q.isNotEmpty()) Box(
                    Modifier.size(20.dp).clip(CircleShape).background(c.ink3).clickable { vm.query.value = "" },
                    contentAlignment = Alignment.Center,
                ) { OdIcon(OdIcon.Close, size = 12.dp, stroke = 2.6f, tint = c.bg) }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            f.group?.let { g -> LitChip(g.label, g, trailing = OdIcon.Close, onClick = { vm.update { it.copy(group = null) } }) }
            FilterMenu("감정", EmotionGroup.entries.map { it.label }) { i -> vm.update { it.copy(group = EmotionGroup.entries[i]) } }
            FilterMenu(f.tag?.let { "#$it" } ?: "태그", listOf("전체") + tags.take(20).map { "#$it" }) { i -> vm.update { it.copy(tag = if (i == 0) null else tags[i - 1]) } }
            val periods = listOf(1, 3, 12, null)
            FilterMenu(f.months?.let { if (it == 12) "최근 1년" else "최근 ${it}개월" } ?: "전체 기간", listOf("최근 1개월", "최근 3개월", "최근 1년", "전체 기간")) { i ->
                vm.update { it.copy(months = periods[i]) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append("${results.size}개") }
                    append("의 일기를 찾았어요")
                },
                style = OdType.caption1, color = c.ink2, modifier = Modifier.weight(1f),
            )
            var open by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.clickable { open = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (f.newestFirst) "최신순" else "오래된순", style = OdType.caption2, color = c.ink2)
                    OdIcon(OdIcon.ChevD, size = 14.dp, tint = c.ink2)
                }
                DropdownMenu(open, { open = false }, containerColor = c.surface) {
                    listOf("최신순" to true, "오래된순" to false).forEach { (l, v) ->
                        DropdownMenuItem({ Text(l, style = OdType.body2) }, { vm.update { it.copy(newestFirst = v) }; open = false })
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            items(results, key = { it.id }) { e -> ResultRow(e, q.trim(), pane && e.id == shown?.id) { if (pane) picked = e.id else onRead(e.id) } }
            if (similar.isNotEmpty()) item(key = "similar") {
                Row(Modifier.padding(top = 22.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("비슷한 내용", style = OdType.title3)
                    com.pass.diary.core.designsystem.AiMark()
                    Text("단어가 달라도 뜻이 가까운 일기예요", style = OdType.caption2, color = c.ink2)
                }
            }
            items(similar, key = { "s" + it.id }) { e -> ResultRow(e, q.trim(), pane && e.id == shown?.id) { if (pane) picked = e.id else onRead(e.id) } }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
    if (pane) {
        val replies by vm.replies.collectAsStateWithLifecycle()
        PaneDivider()
        Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
            if (shown == null) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                OniFigure(null, OniExpression.THINK, 140.dp)
                Text(if (q.isBlank()) "단어나 태그를 넣으면 여기에서 바로 읽을 수 있어요" else "찾은 일기가 없어요", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 12.dp))
            } else DayDetail(LocalDate.parse(shown.date), listOf(shown), replies, onOpen, onWriteOn = {}, single = true, onOpenSimilar = onRead)
        }
    }
    }
}

@Composable
private fun FilterMenu(label: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OdChip(label, outlined = true, height = 32.dp, trailing = OdIcon.ChevD, style = OdType.chip.copy(fontSize = 13.sp), onClick = { open = true })
        DropdownMenu(open, { open = false }, containerColor = Od.c.surface) {
            options.forEachIndexed { i, o -> DropdownMenuItem({ Text(o, style = OdType.body2) }, { onPick(i); open = false }) }
        }
    }
}

private fun searchDate(d: LocalDate): String =
    if (YearMonth.from(d) == YearMonth.now()) "${d.dayOfMonth}일 ${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}"
    else "${d.monthValue}월 ${d.dayOfMonth}일"

@Composable
private fun ResultRow(e: Entry, query: String, selected: Boolean, onClick: () -> Unit) {
    val c = Od.c
    Column(
        Modifier.fillMaxWidth()
            // Selected (detail pane): a soft pill reaching into the list's side padding.
            .then(if (selected) Modifier.drawBehind { val o = 12.dp.toPx(); drawRoundRect(c.surface2, Offset(-o, 4.dp.toPx()), Size(size.width + 2 * o, size.height - 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())) } else Modifier)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(searchDate(LocalDate.parse(e.date)), style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    e.emotions.map { it.key.group }.distinct().forEach { Dot(it?.glowColor ?: OdColors.OffGlow, 8.dp) }
                }
            }
            Text(e.title.ifBlank { e.emotions.joinToString(" · ") { it.key.label } }, style = OdType.title3, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            if (e.body.isNotBlank()) Highlighted(e.body.replace('\n', ' '), query, Modifier.padding(top = 2.dp))
        }
        Divider()
    }
}

/** Body with [query] in bold ink over a calm-glow 35 % band on the lower part of the line (시안 search()). */
@Composable
private fun Highlighted(text: String, query: String, modifier: Modifier) {
    val c = Od.c
    val ranges = if (query.isEmpty()) emptyList() else Regex(Regex.escape(query)).findAll(text).map { it.range }.toList()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val band = EmotionGroup.CALM.glowColor.copy(alpha = 0.35f)
    Text(
        buildAnnotatedString {
            append(text)
            ranges.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink), it.first, it.last + 1) }
        },
        style = OdType.body2, color = c.ink2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            ranges.forEach { r ->
                if (r.last >= l.layoutInput.text.length) return@forEach
                val a = l.getBoundingBox(r.first); val b = l.getBoundingBox(r.last)
                if (l.getLineForOffset(r.first) != l.getLineForOffset(r.last) || l.getLineForOffset(r.first) >= l.lineCount) return@forEach
                val bottom = l.getLineBaseline(l.getLineForOffset(r.first)) + 3.dp.toPx()
                drawRect(band, Offset(a.left, bottom - 6.dp.toPx()), Size(b.right - a.left, 6.dp.toPx()))
            }
        },
    )
}
