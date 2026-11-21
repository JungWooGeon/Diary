package com.pass.diary

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pass.diary.core.designsystem.AiBadge
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.LightOrb
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.designsystem.glowColor
import com.pass.diary.core.designsystem.lightColor
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefs by preferencesDataStore("app_prefs")
private val ONBOARDED = booleanPreferencesKey("onboarded")
private val REMINDER = booleanPreferencesKey("night_reminder")

/** App-level flags (onboarding consent). Lives in :app; settings that features need stay in core:data. */
class AppPrefs(private val context: Context) {
    val onboarded: Flow<Boolean> = context.appPrefs.data.map { it[ONBOARDED] ?: false }
    suspend fun finishOnboarding(reminder: Boolean) = context.appPrefs.edit { it[ONBOARDED] = true; it[REMINDER] = reminder }
}

/** 온보딩 3장 (시안 01): 온이 소개 → 기분은 빛 → 만 14세·약관·AI 안내 동의. */
@Composable
fun Onboarding(onDone: (reminder: Boolean, ai: Boolean) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    AnimatedContent(page, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "onb") { p ->
        when (p) {
            0 -> Intro(onSkip = { page = 2 }, onNext = { page = 1 })
            1 -> MoodIsLight(onBack = { page = 0 }, onSkip = { page = 2 }, onNext = { page = 2 })
            else -> Consent(onBack = { page = 1 }, onDone = onDone)
        }
    }
}

@Composable
private fun PageDots(i: Int) {
    val c = Od.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(3) { j -> Box(Modifier.width(if (j == i) 20.dp else 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ink.copy(alpha = if (j == i) 1f else 0.2f))) }
    }
}

@Composable
private fun BottomCta(page: Int, label: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
        PageDots(page)
        OdButton(label, onClick, Modifier.fillMaxWidth().padding(top = 22.dp), enabled = enabled)
    }
}

@Composable
private fun SkipText(onSkip: () -> Unit) =
    Text("건너뛰기", style = OdType.caption1, color = Od.c.ink2, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSkip).padding(8.dp))

@Composable
private fun Intro(onSkip: () -> Unit, onNext: () -> Unit) {
    val c = Od.c
    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.BrandGlow, 1.3f, 300.dp + 32.dp, 480.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            OdTopBar(right = { SkipText(onSkip) })
            OniFigure(null, OniExpression.DEFAULT, 290.dp, Modifier.padding(top = 56.dp))
            Text("안녕, 나는 온이야", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink2, modifier = Modifier.padding(top = 34.dp))
            Text("하루 끝에 불을 켜 주는\n작은 무드등, 온이예요", style = OdType.display, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            Text("기분을 남기면 온이가 그 색으로 켜지고,\n오늘 이야기를 들어 줘요.", style = OdType.body1, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        }
        BottomCta(0, "다음", onClick = onNext, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MoodIsLight(onBack: () -> Unit, onSkip: () -> Unit, onNext: () -> Unit) {
    val c = Od.c
    val today = LightMixer.mix(listOf(EntryEmotion(EmotionKey.TIRED, 2), EntryEmotion(EmotionKey.COMFY, 3), EntryEmotion(EmotionKey.PROUD, 4), EntryEmotion(EmotionKey.GRATEFUL, 3)))
    Box(Modifier.fillMaxSize().background(c.bg).ambient(rgb(today.glow), 1.2f, 260.dp + 32.dp, 460.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            OdTopBar(left = { IconBtn(OdIcon.Back, onBack, "뒤로") }, right = { SkipText(onSkip) })
            Box(Modifier.fillMaxWidth().height(370.dp).padding(horizontal = 10.dp)) {
                OniFigure(today, OniExpression.HAPPY, 230.dp, Modifier.align(Alignment.TopCenter).padding(top = 112.dp))
                // Five group lights around Oni (시안 coordinates on a 340dp row).
                val spots = listOf(14 to 176, 64 to 66, 150 to 18, 236 to 66, 286 to 176)
                EmotionGroup.entries.zip(spots).forEach { (g, xy) ->
                    Column(
                        Modifier.offset(x = (xy.first - 10).dp, y = xy.second.dp).width(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LightOrb(g.lightColor, g.glowColor, 40.dp, Modifier.dropShadow(CircleShape, Shadow(radius = 14.dp, color = g.glowColor.copy(alpha = 0.45f), offset = DpOffset(0.dp, 4.dp))), stop = 0.4f)
                        Text(g.label, style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
                    }
                }
            }
            Text("오늘 기분이\n온이의 빛이 돼요", style = OdType.display, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text("톡 한 번이면 기록 끝. 여러 감정을 고르면\n빛이 섞여서 오늘만의 색이 돼요.", style = OdType.body1, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
        }
        BottomCta(1, "다음", onClick = onNext, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Consent(onBack: () -> Unit, onDone: (reminder: Boolean, ai: Boolean) -> Unit) {
    val c = Od.c
    var age by rememberSaveable { mutableStateOf(false) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableStateOf(false) }
    var ai by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
            OdTopBar(left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp)) { Oni(null, OniExpression.DEFAULT, Modifier.size(64.dp), animate = false) }
                    Text("마지막으로 몇 가지만 확인할게요", style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink2)
                }
                Text("시작하기 전에\n꼭 알려 드릴게요", style = OdType.display, modifier = Modifier.padding(top = 14.dp))
                OdCard(Modifier.padding(top = 22.dp), padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    CheckRow("만 14세 이상이에요", "필수", age, link = false) { age = it }
                    Divider()
                    CheckRow("이용약관 동의", "필수", terms) { terms = it }
                    CheckRow("개인정보 처리방침 동의", "필수", privacy) { privacy = it }
                    CheckRow("밤 리마인더 받기", "선택", reminder, link = false) { reminder = it }
                }
                OdCard(Modifier.padding(top = 12.dp), flat = true, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiBadge("AI"); Text("온이의 답장은 AI가 써요", style = OdType.title3)
                    }
                    Text("모든 처리는 이 폰 안에서만 해요. 일기는 폰 밖으로 나가지 않아요. 설정에서 언제든 AI를 끌 수 있어요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
                    CheckRow("온이 답장·감정 추천 받기", "선택", ai, link = false) { ai = it }
                    Divider(Modifier.padding(bottom = 12.dp))
                    Text(
                        buildAnnotatedString {
                            append("온이는 의료·상담 서비스가 아니에요. 마음이 많이 힘들 땐 ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append("109") }
                            append("(자살예방상담, 24시간)에 전화해 주세요.")
                        },
                        style = OdType.caption1, color = c.ink2,
                    )
                }
            }
        }
        BottomCta(2, "동의하고 시작하기", enabled = age && terms && privacy, onClick = { onDone(reminder, ai) }, modifier = Modifier.align(Alignment.BottomCenter).background(c.bg))
    }
}

@Composable
private fun CheckRow(text: String, req: String, on: Boolean, link: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Od.c
    Row(Modifier.fillMaxWidth().height(48.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (on) c.primary else c.surface).let { if (on) it else it.border(1.5.dp, c.ink3, CircleShape) },
            contentAlignment = Alignment.Center,
        ) { if (on) OdIcon(OdIcon.Check, size = 16.dp, stroke = 2.4f, tint = c.onPrimary) }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text) }
                withStyle(SpanStyle(color = c.ink2)) { append(" ($req)") }
            },
            style = OdType.body2, modifier = Modifier.weight(1f),
        )
        if (link) OdIcon(OdIcon.ChevR, size = 18.dp, tint = c.ink2)
    }
}

