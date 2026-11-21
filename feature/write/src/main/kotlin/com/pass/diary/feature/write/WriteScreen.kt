package com.pass.diary.feature.write

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.PhotoStore
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.Dot
import com.pass.diary.core.designsystem.EmotionChip
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.LightOrb
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.PaneDivider
import com.pass.diary.core.designsystem.layout
import com.pass.diary.core.designsystem.readableWidth
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdChip
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.dashedBorder
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.lightColor
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntrySource
import com.pass.diary.core.model.Template
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun WriteScreen(onDone: () -> Unit, onSaved: (String) -> Unit, onChat: (String) -> Unit, vm: WriteViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val entry = state.entry ?: return
    val leave = { vm.flush(onDone) }
    val layout = Od.layout
    BackHandler { if (!layout.twoPane && state.step == WriteStep.EDIT && !state.persisted && entry.body.isBlank()) vm.goTo(WriteStep.PICK) else leave() }
    if (layout.twoPane) {
        // Tablets / landscape: feelings on the left, the page on the right — both at once, no step in between.
        Row(Modifier.fillMaxSize().background(Od.c.bg)) {
            Box(Modifier.weight(0.9f)) {
                EmotionPicker(
                    entry, state.focus, vm::toggleEmotion, vm::setIntensity, vm::setDate, vm::setTime,
                    onClose = leave, onMoodOnly = { vm.flush(onDone) }, onContinue = {}, embedded = true,
                )
            }
            PaneDivider()
            Box(Modifier.weight(1.1f)) {
                Editor(state, vm, onBack = leave, onSave = { vm.finish(onSaved) }, onChat = { vm.flush { onChat(entry.id) } }, onDeleted = onDone, embedded = true)
            }
        }
        return
    }
    // Medium (tablet portrait, unfolded foldable): the phone flow in one centered column.
    Box(Modifier.fillMaxSize().background(Od.c.bg)) {
    AnimatedContent(state.step, Modifier.readableWidth(720.dp), transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "step") { step ->
        when (step) {
            WriteStep.PICK -> EmotionPicker(
                entry, state.focus, vm::toggleEmotion, vm::setIntensity, vm::setDate, vm::setTime,
                onClose = leave, onMoodOnly = { vm.flush(onDone) }, onContinue = { vm.goTo(WriteStep.EDIT) },
            )
            WriteStep.EDIT -> Editor(state, vm, onBack = leave, onSave = { vm.finish(onSaved) }, onChat = { vm.flush { onChat(entry.id) } }, onDeleted = onDone)
        }
    }
    }
}

// ============================================================ 1. 감정 고르기

@Composable
private fun EmotionPicker(
    entry: Entry,
    focus: EmotionKey?,
    toggle: (EmotionKey) -> Unit,
    setIntensity: (EmotionKey, Int) -> Unit,
    setDate: (String) -> Unit,
    setTime: (String) -> Unit,
    onClose: () -> Unit,
    onMoodOnly: () -> Unit,
    onContinue: () -> Unit,
    /** Beside the editor (two panes): no close button and no bottom buttons; the editor has them. */
    embedded: Boolean = false,
) {
    val c = Od.c
    val lit = entry.emotions.filter { it.key != EmotionKey.UNSURE }
    val light = if (entry.emotions.isEmpty()) null else LightMixer.mix(entry.emotions)
    val face = when {
        entry.emotions.isEmpty() -> OniExpression.DEFAULT
        lit.isEmpty() -> OniExpression.THINK
        else -> OniExpression.forGroup(LightMixer.dominantGroup(lit))
    }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(c.bg).ambient(light?.let { rgb(it.glow) } ?: OdColors.BrandGlow, 1f, 150.dp + 32.dp, 380.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = if (embedded) 24.dp else 110.dp).then(if (embedded) Modifier.navigationBarsPadding() else Modifier)) {
            Box(Modifier.fillMaxWidth().height(52.dp)) {
                if (!embedded) OdTopBar(left = { IconBtn(OdIcon.Close, onClose, "닫기") })
                DateTimeLabel(entry, Modifier.align(Alignment.Center), onDate = {
                    val d = LocalDate.parse(entry.date)
                    DatePickerDialog(context, { _, y, m, day -> setDate(LocalDate.of(y, m + 1, day).format(EntryRepository.DATE)) }, d.year, d.monthValue - 1, d.dayOfMonth)
                        .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }, onTime = {
                    val t = LocalTime.parse(entry.time)
                    TimePickerDialog(context, { _, h, m -> setTime(LocalTime.of(h, m).format(EntryRepository.TIME)) }, t.hour, t.minute, false).show()
                })
            }
            Row(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OniFigure(light, face, 140.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (entry.emotions.singleOrNull()?.key == EmotionKey.UNSURE) "괜찮아,\n그런 날도 있어" else "오늘 마음은\n어떤 색이에요?",
                        style = OdType.title1,
                    )
                    Text("여러 개 골라도 돼요. 빛이 섞여요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                EmotionGroup.entries.forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.width(58.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(g.glowColor); Spacer(Modifier.width(6.dp))
                            Text(g.label, style = OdType.caption2.copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
                        }
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.emotions.forEach { k ->
                                EmotionChip(k, entry.emotions.firstOrNull { it.key == k }?.intensity, { toggle(k) }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(58.dp))
                    val unsure = entry.emotions.any { it.key == EmotionKey.UNSURE }
                    EmotionChip(EmotionKey.UNSURE, if (unsure) 0 else null, { toggle(EmotionKey.UNSURE) }, Modifier.width(112.dp))
                    Text("빛은 꺼진 채로 남아요", style = OdType.caption2, color = c.ink2)
                }
                val f = entry.emotions.firstOrNull { it.key == focus && it.key != EmotionKey.UNSURE }
                    ?: entry.emotions.lastOrNull { it.key != EmotionKey.UNSURE }
                if (f != null) IntensityCard(f.key, f.intensity) { setIntensity(f.key, it) }
            }
        }
        if (!embedded) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg.copy(alpha = 0f)).navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OdButton("기분만 저장", onMoodOnly, Modifier.weight(1f), kind = ButtonKind.Secondary, enabled = entry.emotions.isNotEmpty())
            OdButton("이어서 쓰기", onContinue, Modifier.weight(1.5f))
        }
    }
}

@Composable
private fun DateTimeLabel(entry: Entry, modifier: Modifier, onDate: () -> Unit, onTime: () -> Unit) {
    val c = Od.c
    Row(
        modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(koDate(entry.date), style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable(onClick = onDate))
        Text(" · ", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold))
        Text(koTime(entry.time), style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable(onClick = onTime))
        OdIcon(OdIcon.ChevD, size = 16.dp, tint = c.ink)
    }
}

/** Intensity 1..5 for the last emotion tapped: dots grow and deepen (README §5). */
@Composable
private fun IntensityCard(key: EmotionKey, value: Int, onSet: (Int) -> Unit) {
    val c = Od.c
    val g = key.group ?: return
    OdCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${key.label}은 얼마나 컸어요?".let { if (hasBatchim(key.label)) it else it.replaceFirst("은 얼", "는 얼") }, style = OdType.title3, modifier = Modifier.weight(1f))
            Text("$value / 5", style = OdType.caption2, color = c.ink2)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            (0 until 5).forEach { i ->
                val size = (22 + i * 4).dp
                val color = when {
                    i >= value -> c.surface2
                    c.dark -> lerp(g.glowColor, Color(0xFFFFF6DA), (0.45f - i * 0.14f).coerceAtLeast(0f))
                    else -> lerp(g.lightColor, g.glowColor, (i / 3f).coerceAtMost(1f))
                }
                Column(
                    Modifier.weight(1f).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onSet(i + 1) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(size).let {
                            if (i == value - 1) it.border(2.dp, g.glowColor, CircleShape).padding(2.dp).border(3.dp, c.surface, CircleShape).padding(1.dp) else it
                        }.clip(CircleShape).background(color),
                    )
                    Text(listOf("조금", "", "보통", "", "아주")[i], style = OdType.caption2.copy(fontSize = 11.sp), color = c.ink2)
                }
            }
        }
    }
}

private fun hasBatchim(word: String): Boolean {
    val ch = word.lastOrNull() ?: return false
    return ch in '가'..'힣' && (ch - '가') % 28 != 0
}

// ============================================================ 2. 에디터

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(state: WriteState, vm: WriteViewModel, onBack: () -> Unit, onSave: () -> Unit, onChat: () -> Unit, onDeleted: () -> Unit, embedded: Boolean = false) {
    val c = Od.c
    val entry = state.entry ?: return
    val context = LocalContext.current
    val light = LightMixer.mix(entry.emotions)
    val glow = if (entry.emotions.isEmpty()) OdColors.BrandGlow else rgb(light.glow)
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by rememberSaveable { mutableStateOf(false) }

    val room = PhotoStore.MAX_PER_ENTRY - entry.photos.size
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(PhotoStore.MAX_PER_ENTRY)) { vm.addPhotos(it) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> vm.addPhotos(listOf(u)) } }
    val pickPhotos = {
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (room >= 2) pickMany.launch(req) else if (room == 1) pickOne.launch(req)
    }
    // 말로 쓰기 (core:device, on-device recognizer) -> raw transcript, then the LLM tidy when installed.
    val dictate = com.pass.diary.core.device.rememberDictation { vm.dictated(it) }
    val weather = com.pass.diary.core.device.rememberWeatherAction(entry.date, vm::setWeather)

    Box(Modifier.fillMaxSize().background(c.bg)) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        OdTopBar(
            left = { IconBtn(OdIcon.Back, onBack, "뒤로") },
            title = null,
            right = { OdButton("저장", onSave, Modifier.padding(end = 4.dp), small = true, height = 36.dp) },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // 감정 요약 줄 → back to the picker (beside it on tablets: not needed)
            if (!embedded) Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(glow.copy(alpha = if (c.dark) 0.2f else 0.16f))
                    .clickable { vm.goTo(WriteStep.PICK) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entry.emotions.isEmpty()) {
                    LightOrb(OdColors.BrandLight, OdColors.BrandGlow, 22.dp)
                    Text("감정 고르기", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                } else {
                    LightOrb(light, 22.dp)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        entry.emotions.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Dot(e.key.group?.glowColor ?: OdColors.OffGlow)
                                Text(if (e.key == EmotionKey.UNSURE) e.key.label else "${e.key.label} ${e.intensity}", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                            }
                        }
                    }
                }
                OdIcon(OdIcon.ChevR, size = 16.dp, tint = c.ink2)
            }
            // ② 온이가 느낀 감정: chips the user taps to add (never applied on their own).
            if (state.suggestedEmotions.isNotEmpty()) Row(
                Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OdIcon(OdIcon.Sparkle, size = 13.dp, stroke = 2f, tint = c.ink2)
                Text("온이가 느낀 감정", style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = c.ink2)
                state.suggestedEmotions.forEach { e ->
                    Row(
                        Modifier.height(30.dp).dashedBorder(e.key.group?.glowColor ?: OdColors.OffGlow, 15.dp).clip(RoundedCornerShape(15.dp))
                            .clickable { vm.acceptEmotion(e) }.padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Dot(e.key.group?.glowColor ?: OdColors.OffGlow)
                        Text("+ ${e.key.label}", style = OdType.chip.copy(fontSize = 13.sp))
                    }
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Template.entries.forEach { t -> OdChip(t.label, selected = entry.template == t, height = 32.dp, style = OdType.chip.copy(fontSize = 13.sp)) { vm.setTemplate(t) } }
            }
            // 제목
            val suggested = state.suggestedTitle
            Box(Modifier.padding(top = 20.dp)) {
                if (entry.title.isEmpty()) Text(suggested ?: "제목", style = OdType.title1, color = if (suggested != null) c.ink else c.ink3.copy(alpha = 0.6f))
                BasicTextField(
                    entry.title, vm::setTitle, Modifier.fillMaxWidth(), textStyle = OdType.title1.copy(color = c.ink),
                    cursorBrush = SolidColor(glow), singleLine = true,
                )
            }
            if (entry.title.isEmpty() && suggested != null) Row(Modifier.padding(top = 2.dp).clickable { vm.setTitle(suggested) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OdIcon(OdIcon.Sparkle, size = 12.dp, stroke = 2f, tint = c.ink2)
                Text("온이가 붙인 제목 · 눌러서 바꾸기", style = OdType.caption2, color = c.ink2)
            }
            // 본문
            Box(Modifier.padding(top = 14.dp)) {
                if (entry.body.isEmpty()) Text("오늘 하루를 들려줘요", style = OdType.diary, color = c.ink3.copy(alpha = 0.7f))
                BasicTextField(
                    entry.body, vm::setBody, Modifier.fillMaxWidth(),
                    textStyle = OdType.diary.copy(color = c.ink), cursorBrush = SolidColor(glow),
                )
            }
            // 사진
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.photos.take(3).forEach { p ->
                    Box(Modifier.size(96.dp)) {
                        AsyncImage(File(p.localPath), null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0x88000000)).clickable { vm.removePhoto(p) },
                            contentAlignment = Alignment.Center,
                        ) { OdIcon(OdIcon.Close, size = 12.dp, stroke = 2.4f, tint = Color.White, contentDescription = "사진 빼기") }
                    }
                }
                if (room > 0 && entry.photos.size < 3) Column(
                    Modifier.size(96.dp).dashedBorder(c.line, 12.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = pickPhotos),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    OdIcon(OdIcon.Plus, size = 20.dp, tint = c.ink3)
                    Text(if (state.importingPhotos) "넣는 중…" else "${entry.photos.size} / 5", style = OdType.caption2, color = c.ink3)
                }
            }
            if (entry.photos.size > 3) Text("+ 사진 ${entry.photos.size - 3}장 더", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(top = 6.dp))
            // 태그
            Column(Modifier.padding(top = 18.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.forEach { t -> OdChip("#${t.value}", height = 30.dp, style = OdType.chip.copy(fontSize = 13.sp)) { vm.removeTag(t) } }
                    if (addingTag) TagInput { vm.addTag(it); addingTag = false }
                }
                if (state.suggestedTags.isNotEmpty()) {
                    Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OdIcon(OdIcon.Sparkle, size = 13.dp, stroke = 2f, tint = c.ink2)
                        Text("온이가 찾은 태그 · 눌러서 추가", style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = c.ink2)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.suggestedTags.forEach { t ->
                            Box(
                                Modifier.height(30.dp).dashedBorder(glow, 15.dp).clip(RoundedCornerShape(15.dp)).clickable { vm.addTag(t.value, t.kind, auto = true) }.padding(horizontal = 13.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("+ ${t.value}", style = OdType.chip.copy(fontSize = 13.sp)) }
                        }
                    }
                }
            }
            if (state.persisted) Text(
                "이 기록 지우기", style = OdType.caption2, color = c.ink3,
                modifier = Modifier.padding(top = 28.dp, bottom = 24.dp).clickable { confirmDelete = true },
            ) else Spacer(Modifier.height(24.dp))
        }
        // 도구 막대
        Column(Modifier.fillMaxWidth().background(c.bg)) {
            com.pass.diary.core.designsystem.Divider()
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(70.dp).padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToolItem(OdIcon.Image, "사진", pickPhotos)
                ToolItem(OdIcon.Tag, "태그") { addingTag = true }
                ToolItem(OdIcon.Sun, com.pass.diary.core.device.weatherLabel(entry.weather) ?: "날씨", weather)
                Spacer(Modifier.weight(1f))
                OdButton("대화로", onChat, kind = ButtonKind.Secondary, small = true, icon = OdIcon.Chat, height = 44.dp)
                OdButton("말로 쓰기", dictate, small = true, icon = OdIcon.Mic, height = 44.dp)
            }
        }
    }
    // Autosave status: centered over the top bar.
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp), contentAlignment = Alignment.Center) {
        if (state.persisted || state.saving) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!state.saving) OdIcon(OdIcon.Check, size = 14.dp, stroke = 2.4f, tint = c.ok)
            Text(if (state.saving) "저장 중…" else "자동 저장됨 · 방금", style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), color = if (state.saving) c.ink2 else c.ok)
        }
    }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text("이 기록을 휴지통으로 옮길까요?\n30일 동안은 마이 › 휴지통에서 되살릴 수 있어요.", style = OdType.body1) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) { Text("지우기", color = c.ink) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", color = c.ink2) } },
        containerColor = c.surface,
    )
}

@Composable
private fun TagInput(onAdd: (String) -> Unit) {
    val c = Od.c
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(c.surface2).padding(horizontal = 13.dp), contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) Text("#태그", style = OdType.chip.copy(fontSize = 13.sp), color = c.ink3)
        BasicTextField(
            text, { text = it }, Modifier.width(96.dp).focusRequester(focus), singleLine = true,
            textStyle = OdType.chip.copy(fontSize = 13.sp, color = c.ink), cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
        )
    }
}

@Composable
private fun ToolItem(icon: OdIcon, label: String, onClick: () -> Unit) {
    val c = Od.c
    Column(Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OdIcon(icon, size = 22.dp, tint = c.ink2)
        Text(label, style = OdType.tab, color = c.ink2)
    }
}

// ============================================================ helpers

internal fun koDate(date: String): String = LocalDate.parse(date).let { "${it.monthValue}월 ${it.dayOfMonth}일" }

internal fun koTime(time: String): String {
    val (h, m) = time.split(":").map { it.toInt() }
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = (h % 12).let { if (it == 0) 12 else it }
    return "$ampm $h12:%02d".format(m)
}
