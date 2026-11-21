package com.pass.diary.feature.write

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pass.diary.core.data.DiaryInsights
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.ReplyReports
import com.pass.diary.core.data.db.ReplyDao
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.designsystem.AiBadge
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.CrisisCard
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.SimilarDays
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@Serializable data class ReplyRoute(val entryId: String)

sealed interface ReplyUi {
    /** A short "reading" beat before the library reply appears. */
    data object Writing : ReplyUi
    /** [fresh] = just written: typed out once. A stored reply (revisit) shows at once. */
    data class Arrived(val text: String, val at: Long, val reported: Boolean, val fresh: Boolean = false) : ReplyUi
    data object Crisis : ReplyUi
    /** 온이 답장 is off in 설정 › AI: just "saved". */
    data object Saved : ReplyUi
}

data class ReplyState(val entry: Entry? = null, val ui: ReplyUi = ReplyUi.Writing, val thanked: Boolean = false, val similar: List<Entry> = emptyList())

@HiltViewModel
class ReplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entries: EntryRepository,
    private val replies: ReplyDao,
    private val provider: ReplyProvider,
    private val reports: ReplyReports,
    private val insights: DiaryInsights,
) : ViewModel() {
    private val id = savedStateHandle.toRoute<ReplyRoute>().entryId
    val state = MutableStateFlow(ReplyState())

    init {
        viewModelScope.launch {
            val entry = entries.get(id) ?: return@launch
            state.update { it.copy(entry = entry) }
            // ① on the entry as it is NOW, before any stored reply: an edit may have added crisis wording since. (An edited
            // body already dropped its old reply in EntryRepository.save, so that case regenerates below.)
            if (provider.isCrisis(entry)) {
                replies.delete(id) // nor shown in 편지함 / calendar
                state.update { it.copy(ui = ReplyUi.Crisis) }
                return@launch
            }
            // E4 under the letter. Not on a crisis day (returned above); similarDays also skips crisis-worded entries.
            launch { runCatching { insights.similarDays(id) }.getOrNull()?.let { s -> state.update { it.copy(similar = s) } } }
            replies.get(id)?.let { r ->
                state.update { it.copy(ui = ReplyUi.Arrived(r.text, r.createdAt, r.reported)) }
                return@launch
            }
            val started = System.currentTimeMillis()
            val since = started - NO_REPEAT_MS
            val recent = replies.observeAll().first().filter { it.createdAt >= since }.mapNotNull { it.templateId }.toSet()
            // Foreground only (ARCHITECTURE §5.3): runs while this screen is open; leaving cancels it and the next visit retries.
            var result: ReplyResult? = null
            runCatching { provider.reply(entry, recent).collect { result = it } }
            if (result == ReplyResult.Off) { state.update { it.copy(ui = ReplyUi.Saved) }; return@launch }
            // The letter takes a moment (README §6); a template reply is instant otherwise.
            delay((MIN_WRITING_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            when (val r = result) {
                is ReplyResult.Letter -> {
                    val now = System.currentTimeMillis()
                    replies.upsert(ReplyEntity(id, r.text, r.source.key, r.templateId, now, reported = false))
                    state.update { it.copy(ui = ReplyUi.Arrived(r.text, now, false, fresh = true)) }
                }
                ReplyResult.Crisis -> state.update { it.copy(ui = ReplyUi.Crisis) }
                ReplyResult.Off -> Unit // handled above
                null -> state.update { it.copy(ui = ReplyUi.Arrived("오늘 얘기 들려줘서 고마워. 내가 잘 간직해 둘게.", System.currentTimeMillis(), false, fresh = true)) }
            }
        }
    }

    fun thank() = state.update { it.copy(thanked = !it.thanked) }

    /** [reason] from [ReplyReports.REASONS]; stays on this phone. */
    fun report(reason: String) = viewModelScope.launch {
        val r = replies.get(id) ?: return@launch
        reports.save(id, reason)
        replies.upsert(r.copy(reported = true))
        state.update { s -> (s.ui as? ReplyUi.Arrived)?.let { s.copy(ui = it.copy(reported = true)) } ?: s }
    }

    private companion object {
        const val MIN_WRITING_MS = 1_800L
        const val NO_REPEAT_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** Crisis light: calm blue, never a warning color (README §5). */
private val CrisisLight = Light(0xD3E2F6, 0x9DB2D8, 0.8f, off = false)

@Composable
fun ReplyScreen(onClose: () -> Unit, onOpenEntry: (String) -> Unit, onLetters: () -> Unit, onOpenSimilar: (String) -> Unit = onOpenEntry, vm: ReplyViewModel = hiltViewModel()) {
    val c = Od.c
    val s by vm.state.collectAsStateWithLifecycle()
    val entry = s.entry
    val ui = s.ui
    BackHandler(onBack = onClose)
    val crisis = ui is ReplyUi.Crisis
    val light = when {
        crisis -> CrisisLight
        entry == null || entry.emotions.isEmpty() -> null
        else -> LightMixer.mix(entry.emotions)
    }
    val expression = when (ui) {
        is ReplyUi.Writing -> OniExpression.LETTER
        ReplyUi.Crisis -> OniExpression.COMFORT
        ReplyUi.Saved -> OniExpression.DEFAULT
        is ReplyUi.Arrived -> OniExpression.forState(entry?.emotions.orEmpty(), justSaved = true).let { if (it == OniExpression.SLEEP) OniExpression.CHEER else it }
    }
    val glow = light?.let { rgb(it.glow) } ?: OdColors.BrandGlow
    // Landscape phones: the buttons follow the letter instead of floating over it.
    val short = Od.layout.short
    val buttons: @Composable () -> Unit = {
            when (ui) {
                is ReplyUi.Writing -> OdButton("답장은 나중에 볼게요", onClose, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                ReplyUi.Crisis -> OdButton("괜찮아요, 일기로 돌아갈게요", { entry?.let { onOpenEntry(it.id) } ?: onClose() }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                is ReplyUi.Arrived, ReplyUi.Saved -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OdButton("일기 보기", { entry?.let { onOpenEntry(it.id) } }, Modifier.weight(1f), kind = ButtonKind.Secondary)
                    OdButton("확인", onClose, Modifier.weight(1f))
                }
            }
    }
    Box(Modifier.fillMaxSize().background(c.bg).ambient(glow, if (crisis) 0.9f else 1.5f, 230.dp + 32.dp, 520.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = if (short) 0.dp else 110.dp)) {
            OdTopBar(left = { IconBtn(OdIcon.Close, onClose, "닫기") })
            OniFigure(light, expression, if (Od.layout.short) 120.dp else if (crisis) 200.dp else 236.dp, Modifier.align(Alignment.CenterHorizontally).padding(top = if (crisis) 6.dp else 20.dp))
            Column(Modifier.readableWidth(560.dp).padding(horizontal = 20.dp).padding(top = if (crisis) 4.dp else 10.dp)) {
                when (ui) {
                    is ReplyUi.Writing, ReplyUi.Saved -> Text("일기를 저장했어요", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    ReplyUi.Crisis -> Text("이번엔 답장 대신 이 얘기를 꼭 하고 싶었어", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    is ReplyUi.Arrived -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Dot(glow); Text("오늘 빛이 바뀌었어요", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                AnimatedContent(
                    ui, Modifier.padding(top = 14.dp),
                    transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 12 }) togetherWith fadeOut(tween(120)) },
                    contentKey = { it::class }, label = "reply",
                ) { u ->
                    when (u) {
                        is ReplyUi.Writing -> WritingCard(glow)
                        is ReplyUi.Arrived -> LetterCard(u, s.thanked, vm::thank, onLetters, vm::report)
                        ReplyUi.Crisis -> CrisisCard()
                        ReplyUi.Saved -> SavedCard()
                    }
                }
                if (ui is ReplyUi.Arrived || ui is ReplyUi.Saved) SimilarDays(s.similar, onOpenSimilar, Modifier.padding(top = 16.dp))
            }
            if (short) Box(Modifier.readableWidth(560.dp).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) { buttons() }
        }
        if (!short) Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().readableWidth(560.dp).padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) { buttons() }
    }
}

@Composable
private fun WritingCard(glow: Color) {
    val c = Od.c
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x")
    OdCard(padding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AiBadge("온이", icon = false)
        }
        Text("온이가 일기를 읽고 있어요", style = OdType.title2, modifier = Modifier.padding(top = 14.dp))
        listOf(1f, 0.92f, 0.64f).forEachIndexed { i, w ->
            Box(
                Modifier.padding(top = if (i == 0) 14.dp else 10.dp).fillMaxWidth(w).height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to c.surface2, ((x - 0.25f).coerceIn(0f, 1f)) to c.surface2, (x.coerceIn(0f, 1f)) to glow.copy(alpha = 0.45f),
                            ((x + 0.25f).coerceIn(0f, 1f)) to c.surface2, 1f to c.surface2,
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun LetterCard(u: ReplyUi.Arrived, thanked: Boolean, onThank: () -> Unit, onLetters: () -> Unit, onReport: (String) -> Unit) {
    val c = Od.c
    val context = LocalContext.current
    var reportAsk by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf<String?>(null) }
    OdCard(padding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AiBadge("온이의 한마디", icon = false); Spacer(Modifier.weight(1f))
            Text(koClock(u.at), style = OdType.caption2, color = c.ink2)
        }
        // A reported reply is hidden (kept only as the record that it was reported).
        if (u.reported) Text("신고한 답장은 숨겼어요.", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 14.dp))
        else TypedText(u.text, animate = u.fresh, Modifier.padding(top = 14.dp))
        Text("— 온이가", style = OdType.caption1, color = c.ink2, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        Divider(Modifier.padding(top = 16.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Action(OdIcon.Heart, "고마워", if (thanked) glowOrInk() else c.ink, bold = true, onClick = onThank)
            Action(OdIcon.Mail, if (u.reported) "편지함에서 뺐어요" else "편지함에 보관됨", c.ink2, onClick = onLetters)
            Action(OdIcon.Flag, if (u.reported) "신고됨" else "신고", c.ink2) {
                if (!u.reported) reportAsk = true
            }
        }
    }
    if (reportAsk) androidx.compose.material3.AlertDialog(
        onDismissRequest = { reportAsk = false },
        title = { Text("이 답장을 신고할까요?", style = OdType.title3) },
        text = {
            Column {
                Text("어떤 점이 불편했는지 골라 주세요. 신고한 답장은 숨기고 편지함에서도 빼 둘게요. 신고 내용은 이 폰에만 남아요.", style = OdType.body2, color = c.ink2)
                Spacer(Modifier.height(8.dp))
                ReplyReports.REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .selectable(reason == r, role = androidx.compose.ui.semantics.Role.RadioButton) { reason = r }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(if (reason == r) c.primary else c.surface).border(1.5.dp, if (reason == r) c.primary else c.ink3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (reason == r) Box(Modifier.size(8.dp).clip(CircleShape).background(c.onPrimary)) }
                        Text(r, style = OdType.body2)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = reason != null, onClick = {
                reportAsk = false; onReport(reason!!); Toast.makeText(context, "신고했어요. 고마워요.", Toast.LENGTH_SHORT).show()
            }) { Text("신고", color = if (reason != null) c.ink else c.ink3.copy(alpha = 0.45f)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { reportAsk = false }) { Text("취소", color = c.ink2) } },
        containerColor = c.surface,
    )
}

@Composable private fun glowOrInk() = Color(0xFFEE8BA5)

/**
 * The accepted reply typed out once (~30 ms a letter, a breath after each sentence). The rest is laid out transparent
 * so the card doesn't grow line by line; screen readers get the whole text. Off when system animations are off.
 */
@Composable
private fun TypedText(text: String, animate: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val motion = remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    var shown by rememberSaveable(text) { mutableIntStateOf(if (animate && motion) 0 else text.length) }
    LaunchedEffect(text) {
        while (shown < text.length) { delay(if (text[shown] in ".!?…\n") 260L else 32L); shown++ }
    }
    Text(
        buildAnnotatedString { append(text.take(shown)); withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(shown)) } },
        style = OdType.reply, modifier = modifier.semantics { contentDescription = text },
    )
}

/** 온이 답장 off: the entry is saved, nothing from the AI. */
@Composable
private fun SavedCard() {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp)) {
        Text("오늘 일기를 잘 간직해 둘게요", style = OdType.title2)
        Text("온이 답장은 꺼 두었어요. 마이 › AI 기능에서 다시 켤 수 있어요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Action(icon: OdIcon, label: String, color: Color, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OdIcon(icon, size = 17.dp, tint = color)
        Text(label, style = OdType.caption1.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = color)
    }
}

internal fun koClock(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return koTime("%02d:%02d".format(t.hour, t.minute))
}
