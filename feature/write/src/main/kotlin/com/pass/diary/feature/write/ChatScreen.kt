package com.pass.diary.feature.write

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.ChatMessageDao
import com.pass.diary.core.data.db.ChatMessageEntity
import com.pass.diary.core.designsystem.CrisisCard
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdChip
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.odCard
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EntrySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** [entryId] = continue that entry (from the editor); null = a new entry today. */
@Serializable data class ChatRoute(val entryId: String? = null)

/** [crisis] = an answer hit crisis detection: the chat stops (no question, no compose) and shows the help card. */
data class ChatState(
    val turns: List<ChatTurn> = emptyList(), val thinking: Boolean = false, val light: Light? = null, val done: Boolean = false,
    val crisis: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val entries: EntryRepository,
    private val chat: ChatMessageDao,
    val responder: ChatResponder,
) : ViewModel() {
    private val route = handle.toRoute<ChatRoute>()
    /** A new chat writes into its own draft entry from the first answer; its id survives process death in [handle]. */
    private var entryId: String? = route.entryId ?: handle[KEY_DRAFT]
    private val saveLock = Mutex()
    private var persisted = 0 // turns already in chat_message
    private var lastAt = 0L
    val state = MutableStateFlow(ChatState())

    init {
        viewModelScope.launch {
            val e = entryId?.let { entries.get(it) }
            // Resume a conversation kept with the entry.
            val rows = entryId?.let { chat.get(it) }.orEmpty()
            val saved = rows.map { ChatTurn(it.role == "oni", it.text) }
            persisted = saved.size; lastAt = rows.maxOfOrNull { it.at } ?: 0L
            state.update { it.copy(light = e?.emotions?.takeIf { l -> l.isNotEmpty() }?.let(LightMixer::mix), turns = saved) }
            if (saved.any { !it.fromOni && responder.isCrisis(it.text) }) { state.update { it.copy(crisis = true) }; return@launch }
            if (saved.lastOrNull()?.fromOni != true) ask()
        }
    }

    val questionsAsked get() = state.value.turns.count { it.fromOni }
    /** Something the user said that isn't in a composed entry yet: back asks first. */
    val hasAnswers get() = state.value.turns.any { !it.fromOni }

    private suspend fun ask() {
        state.update { it.copy(thinking = true) }
        val next = runCatching { responder.next(state.value.turns) }.getOrNull()
        state.update { s -> if (next == null) s.copy(thinking = false, done = true) else s.copy(turns = s.turns + ChatTurn(true, next), thinking = false) }
        saveTurns()
    }

    /** false = not taken (Oni still thinking / chat stopped): the input keeps the text. */
    fun send(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || state.value.thinking || state.value.crisis) return false
        state.update { it.copy(turns = it.turns + ChatTurn(false, t), thinking = true) }
        viewModelScope.launch {
            saveTurns() // on disk before anything else runs: relock / back / process death never loses it
            // ① on every answer (ARCHITECTURE §5.1).
            if (responder.isCrisis(t)) { state.update { it.copy(thinking = false, crisis = true) }; return@launch }
            if (questionsAsked < responder.maxQuestions) ask() else state.update { it.copy(thinking = false, done = true) }
        }
        return true
    }

    fun skip() {
        if (state.value.thinking || state.value.crisis) return
        viewModelScope.launch { if (questionsAsked < responder.maxQuestions) ask() }
    }

    /**
     * Writes turns not yet in chat_message. Nothing is written before the first answer; then the entry is created if
     * needed. While the body is still just the chat's raw answers (or empty), it follows them, so the user's words are
     * an entry even if the chat is never composed.
     */
    private suspend fun saveTurns() = withContext(NonCancellable) {
        saveLock.withLock {
            val turns = state.value.turns
            if (turns.size <= persisted || turns.none { !it.fromOni }) return@withLock
            val id = entryId ?: EntryRepository.newEntry().id.also { entryId = it; handle[KEY_DRAFT] = it }
            val e = entries.get(id) ?: EntryRepository.newEntry().copy(id = id, source = EntrySource.CHAT)
            val before = answers(turns.take(persisted))
            if (e.body.isBlank() || e.body == before) entries.save(e.copy(body = answers(turns), source = EntrySource.CHAT))
            turns.drop(persisted).forEach { t ->
                lastAt = maxOf(System.currentTimeMillis(), lastAt + 1)
                chat.insert(ChatMessageEntity(entryId = id, role = if (t.fromOni) "oni" else "user", text = t.text, at = lastAt))
            }
            persisted = turns.size
        }
    }

    /** "여기까지로 일기 만들기": compose → save (chat already kept with the entry) → editor. */
    fun compose(then: (String) -> Unit) = viewModelScope.launch {
        val turns = state.value.turns
        if (turns.none { !it.fromOni } || state.value.crisis) return@launch
        state.update { it.copy(thinking = true) }
        if (turns.any { !it.fromOni && responder.isCrisis(it.text) }) { state.update { it.copy(thinking = false, crisis = true) }; return@launch }
        saveTurns()
        val body = runCatching { responder.compose(turns) }.getOrElse { answers(turns) }
        val now = System.currentTimeMillis()
        val id = entryId ?: return@launch
        val base = entries.get(id) ?: EntryRepository.newEntry(now = now).copy(id = id)
        val raw = answers(turns)
        val merged = if (base.body.isBlank() || base.body == raw) body else base.body.trimEnd() + "\n\n" + body
        val saved = entries.save(base.copy(body = merged, source = EntrySource.CHAT), now)
        then(saved.id)
    }

    /** Back → "지우고 나가기": only a draft this chat created goes (an entry opened from the editor stays). */
    val ownsDraft get() = route.entryId == null && entryId != null

    fun discard(then: () -> Unit) = viewModelScope.launch {
        if (ownsDraft) withContext(NonCancellable) { entryId?.let { entries.delete(it) } }
        then()
    }

    private companion object {
        const val KEY_DRAFT = "chatDraftId"
        fun answers(turns: List<ChatTurn>) = turns.filterNot { it.fromOni }.joinToString("\n") { it.text }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(onBack: () -> Unit, onComposed: (String) -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val c = Od.c
    val s by vm.state.collectAsStateWithLifecycle()
    var askLeave by remember { mutableStateOf(false) }
    // Answers are already saved; back still asks so nothing is left behind (or thrown away) by accident.
    val back = { if (vm.hasAnswers && !s.crisis) askLeave = true else onBack() }
    BackHandler(onBack = back)
    val glow = s.light?.let { rgb(it.glow) } ?: EmotionGroup.CALM.glowColor
    val light = s.light ?: LightMixer.mix(listOf(com.pass.diary.core.model.EntryEmotion(com.pass.diary.core.model.EmotionKey.COMFY)))
    val max = vm.responder.maxQuestions
    val asked = s.turns.count { it.fromOni }
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(s.turns.size) { if (s.turns.isNotEmpty()) list.animateScrollToItem(s.turns.size) }

    Box(Modifier.fillMaxSize().background(c.bg).ambient(glow, 0.6f, 80.dp + 32.dp, 360.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            OdTopBar(
                title = "온이와 대화로 쓰기", subtitle = "이 폰 안에서만 대화해요",
                left = { IconBtn(OdIcon.Back, back, "뒤로") },
            )
            Row(Modifier.readableWidth(ChatWidth).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(max) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < asked) glow else c.line)) }
            }
            LazyColumn(Modifier.weight(1f).readableWidth(ChatWidth), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                item {
                    Text(
                        "질문 ${asked.coerceAtLeast(1)} / $max · 언제든 일기로 만들 수 있어요", style = OdType.caption2, color = c.ink2,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
                itemsIndexed(s.turns) { i, t -> if (t.fromOni) OniBubble(t.text, light, first = i == 0) else MyBubble(t.text, glow) }
                if (s.crisis) item {
                    Text(
                        "이번엔 질문 대신 이 얘기를 꼭 하고 싶었어", style = OdType.caption1, color = c.ink2,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                    )
                    CrisisCard()
                }
                if (!s.crisis) item {
                    val sug = if (s.thinking || s.turns.lastOrNull()?.fromOni != true) emptyList() else vm.responder.suggestions(s.turns)
                    if (s.thinking) OniBubble("…", light, first = false)
                    if (sug.isNotEmpty()) FlowRow(Modifier.padding(start = 42.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sug.forEach { q -> OdChip(q, outlined = true, height = 32.dp, style = OdType.chip.copy(fontSize = 13.sp)) { if (q == "다음 질문") vm.skip() else vm.send(q) } }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(c.bg)) {
                com.pass.diary.core.designsystem.Divider()
                if (s.crisis) Column(Modifier.navigationBarsPadding().readableWidth(ChatWidth).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
                    OdButton("괜찮아요, 나갈게요", onBack, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                } else Column(Modifier.navigationBarsPadding().readableWidth(ChatWidth).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
                    OdButton(
                        "여기까지로 일기 만들기", { vm.compose(onComposed) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, small = true,
                        icon = OdIcon.Doc, height = 42.dp, enabled = s.turns.any { !it.fromOni },
                    )
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(23.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (text.isEmpty()) Text("답장을 입력해요", style = OdType.body2.copy(fontSize = 14.5.sp), color = c.ink3)
                            BasicTextField(
                                text, { text = it }, Modifier.fillMaxWidth(), textStyle = OdType.body2.copy(fontSize = 14.5.sp, color = c.ink),
                                cursorBrush = SolidColor(glow), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { if (vm.send(text)) text = "" }), maxLines = 4,
                            )
                        }
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c.surface2).clickable { }, contentAlignment = Alignment.Center) { OdIcon(OdIcon.Mic, size = 21.dp) }
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c.primary).clickable { if (vm.send(text)) text = "" }, contentAlignment = Alignment.Center) {
                            OdIcon(OdIcon.Send, size = 20.dp, stroke = 2f, tint = c.onPrimary, contentDescription = "보내기")
                        }
                    }
                }
            }
        }
    }
    if (askLeave) LeaveDialog(
        vm.ownsDraft,
        onCompose = { askLeave = false; vm.compose(onComposed) },
        onKeep = { askLeave = false; onBack() },
        onDiscard = { askLeave = false; vm.discard(onBack) },
        onDismiss = { askLeave = false },
    )
}

@Composable
private fun LeaveDialog(ownsDraft: Boolean, onCompose: () -> Unit, onKeep: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대화를 어떻게 할까요?", style = OdType.title3) },
        text = { Text("지금까지 답한 내용은 이 폰에 저장돼 있어요. 나중에 이 일기에서 ‘대화로’를 누르면 이어서 할 수 있어요.", style = OdType.body2, color = Od.c.ink2) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onCompose) { Text("일기로 만들기") } },
        dismissButton = {
            Row {
                if (ownsDraft) androidx.compose.material3.TextButton(onClick = onDiscard) { Text("지우고 나가기", color = Od.c.ink2) }
                androidx.compose.material3.TextButton(onClick = onKeep) { Text("저장하고 나가기") }
            }
        },
    )
}

@Composable
private fun OniBubble(text: String, light: Light, first: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = if (first) 16.dp else 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(34.dp)) { Oni(light, OniExpression.CALM, Modifier.size(34.dp), animate = false) }
        Box(Modifier.widthIn(max = 250.dp).odCard(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).padding(horizontal = 15.dp, vertical = 11.dp)) {
            Text(text, style = OdType.body2)
        }
    }
}

@Composable
private fun MyBubble(text: String, glow: androidx.compose.ui.graphics.Color) {
    val c = Od.c
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(glow.copy(alpha = if (c.dark) 0.3f else 0.22f))
                .padding(horizontal = 15.dp, vertical = 11.dp),
        ) { Text(text, style = OdType.body2) }
    }
}

/** A conversation stays a column on tablets: bubbles don't drift to the far edges. */
private val ChatWidth = 720.dp
