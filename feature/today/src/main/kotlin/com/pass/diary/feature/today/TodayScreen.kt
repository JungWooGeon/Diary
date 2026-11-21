package com.pass.diary.feature.today

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.designsystem.AiBadge
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.CardHeader
import com.pass.diary.core.designsystem.DayLightStrip
import com.pass.diary.core.designsystem.DayMark
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.EmptyDayStrip
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.readableWidth
import androidx.compose.foundation.layout.fillMaxHeight
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.PhotoPlaceholder
import com.pass.diary.core.designsystem.QuickMoodRow
import com.pass.diary.core.designsystem.SpeechBubble
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.TagKind
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@Serializable data object TodayRoute

data class RecentReply(val reply: ReplyEntity, val entry: Entry?)

data class TodayUi(
    val date: LocalDate,
    val entries: List<Entry>,
    val yearAgo: Entry?,
    val recent: RecentReply?,
    val question: String,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository,
    replies: ReplyDao,
) : ViewModel() {
    /** Set for ~3 s after a 1-tap save: Oni cheers / comforts once (README §6). */
    val justSaved = MutableStateFlow<EmotionKey?>(null)

    // Re-reads "today" each time the screen is collected, so a screen left open past midnight shows the new day.
    @OptIn(ExperimentalCoroutinesApi::class)
    val ui = flow { emit(LocalDate.now()) }.flatMapLatest { today ->
        val yearAgo = today.minusYears(1).toString()
        combine(
            repository.observeDay(today.toString()),
            repository.observeDay(yearAgo),
            replies.observeAll().flatMapLatest { all ->
                val r = all.firstOrNull { !it.reported } ?: return@flatMapLatest flowOf(null)
                repository.observe(r.entryId).map { RecentReply(r, it) }
            },
        ) { day, ago, recent -> TodayUi(today, day, ago.firstOrNull(), recent, question(today, day)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var questions: List<Pair<String?, String>>? = null

    /** 오늘의 질문 (shared/library/questions.json): same question all day, matched to the time of day. */
    private suspend fun question(today: LocalDate, day: List<Entry>): String {
        val all = questions ?: withContext(Dispatchers.IO) {
            runCatching {
                Json.parseToJsonElement(context.assets.open("library/questions.json").bufferedReader().use { it.readText() }).jsonArray.map {
                    it.jsonObject["time"]?.jsonPrimitive?.content to it.jsonObject["text"]!!.jsonPrimitive.content
                }
            }.getOrDefault(emptyList())
        }.also { questions = it }
        if (all.isEmpty()) return "오늘 하루 중 다시 돌아가고 싶은 순간이 있다면?"
        val slot = when (LocalTime.now().hour) { in 5..10 -> "morning"; in 11..17 -> "day"; else -> "night" }
        val usable = all.filter { '{' !in it.second } // slot templates ({person}…) need the AI to fill them
        val pool = usable.filter { it.first == null || it.first == slot }.ifEmpty { usable.ifEmpty { return "오늘 하루 중 다시 돌아가고 싶은 순간이 있다면?" } }
        return pool[abs((today.toEpochDay() * 31 + day.size / 3).toInt()) % pool.size].second
    }

    /**
     * Saves at once; the returned id goes with that save's snackbar, so 되돌리기 removes exactly that entry even when
     * tapped before the save finished or after another mood was tapped.
     */
    fun quickMood(key: EmotionKey): Deferred<String> = viewModelScope.async {
        val id = repository.quickMood(key).id
        justSaved.value = key
        viewModelScope.launch { delay(3_000); if (justSaved.value == key) justSaved.value = null } // not a child: await() must not wait 3 s
        id
    }

    fun undoQuick(saved: Deferred<String>) = viewModelScope.launch {
        repository.delete(saved.await())
        justSaved.value = null
    }
}

@Composable
fun TodayScreen(
    onWrite: (mood: String?) -> Unit,
    onWriteQuestion: (String) -> Unit,
    onOpen: (entryId: String) -> Unit,
    onLetters: () -> Unit,
    onBackup: () -> Unit = {},
    vm: TodayViewModel = hiltViewModel(),
) {
    val c = Od.c
    val ui by vm.ui.collectAsStateWithLifecycle()
    val justSaved by vm.justSaved.collectAsStateWithLifecycle()
    val u = ui ?: return
    val all = u.entries.flatMap { it.emotions }
    val empty = u.entries.isEmpty()
    val light: Light = if (empty) LightMixer.OFF else LightMixer.mixDay(u.entries.map { it.emotions })
    var poke by remember { mutableIntStateOf(0) }
    val expression = when {
        poke % 3 == 1 -> OniExpression.SURPRISED
        justSaved != null -> OniExpression.forState(all, justSaved = true)
        empty -> OniExpression.SLEEP
        light.off -> OniExpression.THINK
        else -> OniExpression.forGroup(LightMixer.dominantGroup(all))
    }
    LaunchedEffect(poke) { if (poke % 3 == 1) { delay(1_200); poke++ } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ambientGlow = if (empty) OdColors.Dusk else rgb(light.glow)

    val layout = Od.layout
    val headerHero: @Composable () -> Unit = {
        // Header: date · weather · bell
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${u.date.monthValue}월 ${u.date.dayOfMonth}일 ${u.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}", style = OdType.title2, modifier = Modifier.weight(1f))
            u.entries.lastOrNull { it.weather != null }?.weather?.let { w ->
                Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OdIcon(OdIcon.Sun, size = 17.dp, tint = c.ink2); Text(com.pass.diary.core.data.Weather.decode(w)?.label ?: w, style = OdType.caption1, color = c.ink2)
                }
            }
        }
        // Hero: Oni + speech bubble
        Box(Modifier.fillMaxWidth().height(if (empty) 270.dp else 300.dp)) {
            OniFigure(
                light.takeUnless { empty }, expression, 230.dp,
                Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { poke++ },
            )
            SpeechBubble(oniSays(u, light), Modifier.align(Alignment.TopCenter).padding(top = 4.dp, start = 20.dp, end = 20.dp))
        }
    }
    val moods: @Composable () -> Unit = {
        val pick: (EmotionKey) -> Unit = { k ->
            val saved = vm.quickMood(k)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar("${k.label}으로 기록했어요".josa(k.label), actionLabel = "되돌리기", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.undoQuick(saved)
            }
        }
        if (empty) OdCard(Modifier.padding(top = 4.dp)) {
            Text("지금 기분은 어때요?", style = OdType.title2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("하나만 눌러도 온이가 켜져요", style = OdType.caption1, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp))
            QuickMoodRow(justSaved, pick)
            OdButton("여러 감정 고르고 쓰기", { onWrite(null) }, Modifier.fillMaxWidth().padding(top = 16.dp), kind = ButtonKind.Secondary, small = true, icon = OdIcon.Pen, height = 44.dp)
        } else OdCard(Modifier.padding(top = 4.dp), padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)) {
            CardHeader("지금 기분은 어때요?", "톡 누르면 바로 저장돼요", Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp), rightStyle = OdType.caption2)
            QuickMoodRow(justSaved, pick)
        }
    }
    val cards: @Composable () -> Unit = {
        // 하루의 빛
        OdCard {
            if (empty) {
                CardHeader("하루의 빛", "아직 켜진 빛이 없어요", rightStyle = OdType.caption2)
                EmptyDayStrip(Modifier.padding(top = 14.dp))
                Text("아침·점심·밤, 기록할 때마다 빛이 하나씩 이어져요.", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
            } else {
                CardHeader("하루의 빛", "오늘 ${u.entries.size}번 기록했어요", Modifier.padding(bottom = 14.dp), rightStyle = OdType.caption2)
                DayLightStrip(u.entries.map { DayMark(it.time, it.emotions) })
                // Today's records, newest first: tap to open.
                u.entries.reversed().forEach { e -> EntryLine(e) { onOpen(e.id) } }
            }
        }
        // 오늘의 질문
        OdCard(Modifier.fillMaxWidth(), onClick = { onWriteQuestion(u.question) }) {
            Text("오늘의 질문", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
            Text(u.question, style = OdType.title3.copy(fontSize = 16.sp, lineHeight = 24.sp), modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("이 질문으로 쓰기", style = OdType.buttonSm)
                OdIcon(OdIcon.ChevR, size = 16.dp, stroke = 2f)
            }
        }
        // 1년 전 오늘
        u.yearAgo?.let { e ->
            OdCard(onClick = { onOpen(e.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val p = e.photos.firstOrNull()
                    if (p != null && File(p.localPath).exists()) AsyncImage(File(p.localPath), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    else PhotoPlaceholder(e.id.hashCode(), Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dot(LightMixer.dominantGroup(e.emotions)?.glowColor ?: OdColors.OffGlow)
                            val d = LocalDate.parse(e.date)
                            Text("1년 전 오늘 · ${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                        }
                        Text(e.body.ifBlank { e.title }, style = OdType.body2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        // 최근 답장
        u.recent?.let { r ->
            val rl = r.entry?.emotions?.takeIf { it.isNotEmpty() }?.let(LightMixer::mix)
            OdCard(onClick = onLetters) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("최근 답장", style = OdType.title3, modifier = Modifier.weight(1f))
                    AiBadge("온이(AI)가 쓴 답장", icon = false)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Oni(rl, OniExpression.forGroup(r.entry?.emotions?.let(LightMixer::dominantGroup)).takeUnless { it == OniExpression.SLEEP } ?: OniExpression.DEFAULT, Modifier.size(40.dp), animate = false)
                    Text(r.reply.text, style = OdType.body2, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text("${relDay(r.reply.createdAt)} · 편지함에 보관됨", style = OdType.caption2, color = c.ink2, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        }
    }
    Box(Modifier.fillMaxSize().background(c.bg).then(if (layout.twoPane) Modifier else Modifier.ambient(ambientGlow, if (empty) 0.55f else 1.15f, 190.dp + 32.dp, 460.dp))) {
        if (layout.twoPane) Row(Modifier.fillMaxSize()) {
            // Tablet / landscape: Oni and the mood orbs stay in view on the left; the day's records scroll on the right.
            Column(
                Modifier.weight(1f).fillMaxHeight().ambient(ambientGlow, if (empty) 0.55f else 1.15f, 190.dp + 32.dp + 24.dp, 460.dp)
                    .statusBarsPadding().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 24.dp).navigationBarsPadding(),
            ) {
                headerHero()
                Column(Modifier.readableWidth(480.dp).padding(horizontal = 20.dp)) { moods() }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().statusBarsPadding().verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 24.dp, top = 76.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { BackupNoticeBar(onBackup); cards() }
        } else Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            Column(Modifier.readableWidth()) { BackupNoticeBar(onBackup, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)); headerHero() }
            Column(Modifier.readableWidth().padding(horizontal = 20.dp).padding(bottom = layout.bottomBar + 42.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                moods()
                cards()
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = layout.bottomBar + 24.dp))
    }
}

@Composable
private fun EntryLine(e: Entry, onClick: () -> Unit) {
    val c = Od.c
    val text = e.title.ifBlank { e.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
    if (text.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(LightMixer.dominantGroup(e.emotions)?.glowColor ?: OdColors.OffGlow, 8.dp)
        Text(e.time, style = OdType.caption2, color = c.ink2)
        Text(text, style = OdType.body2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        OdIcon(OdIcon.ChevR, size = 14.dp, tint = c.ink3)
    }
}

/** Oni's line (다정한 반말). Mentions today's activity tag when there is one (README §1.5). */
private fun oniSays(u: TodayUi, light: Light): String {
    if (u.entries.isEmpty()) return "불 켜 둘게… 오늘 얘기 들려줘."
    if (light.off) return "괜찮아, 그런 날도 있어."
    val g = LightMixer.dominantGroup(u.entries.flatMap { it.emotions })
    val act = u.entries.flatMap { it.tags }.lastOrNull { it.kind == TagKind.ACTIVITY }?.value
    return when (g) {
        EmotionGroup.BRIGHT -> (act?.let { "${it}하고 와서 뿌듯했구나.\n" } ?: "좋은 일이 있었구나.\n") + "오늘 빛, 참 따뜻하다."
        EmotionGroup.CALM -> (act?.let { "$it 덕분에 마음이 잔잔하네.\n" } ?: "잔잔한 하루였네.\n") + "이 빛, 오래 켜 둘게."
        EmotionGroup.FLUTTER -> "두근거리는 게 여기까지 전해져.\n무슨 일인지 더 들려줘."
        EmotionGroup.LOW -> "오늘은 조금 무거웠구나.\n나 여기 불 켜 두고 있을게."
        EmotionGroup.WORN -> "많이 지쳤지.\n오늘은 일찍 불 줄이고 쉬자."
        null -> "괜찮아, 그런 날도 있어."
    }
}

/** "기쁨으로" vs "편안함으로" — 으로/로 by 받침 (ㄹ 받침 → 로). */
private fun String.josa(word: String): String {
    val ch = word.last()
    val jong = if (ch in '가'..'힣') (ch - '가') % 28 else 0
    return if (jong == 0 || jong == 8) replace("${word}으로", "${word}로") else this
}

private fun relDay(millis: Long): String {
    val z = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val d = z.toLocalDate()
    val t = z.toLocalTime()
    val clock = "${if (t.hour < 12) "오전" else "오후"} ${(t.hour % 12).let { if (it == 0) 12 else it }}:%02d".format(t.minute)
    return when (d) {
        LocalDate.now() -> clock
        LocalDate.now().minusDays(1) -> "어제 $clock"
        else -> "${d.monthValue}월 ${d.dayOfMonth}일"
    }
}
