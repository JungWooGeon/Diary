package com.pass.diary.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.pass.diary.core.billing.CardTheme
import com.pass.diary.core.billing.StoryShareDialog
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.Insights
import com.pass.diary.core.designsystem.AiBadge
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
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

/** E10 주간 온이 편지. [offset] 0 = this week (일요일 밤 알림), -1 = last week… */
@Serializable data class WeeklyLetterRoute(val offset: Int = 0)

data class WeeklyLetter(
    val start: LocalDate,
    val label: String,
    val light: Light,
    val dominant: EmotionGroup?,
    val days: Int,
    /** 월..일, null = no record that day. */
    val dayLights: List<Light?>,
    val top: List<Pair<EmotionKey, Int>>,
    /** Letter body, Oni's 반말, assembled from templates (온이의 한마디, not AI). */
    val paragraphs: List<String>,
    val cheer: String,
    /** 이번 주의 한 줄 (E7 over the week): picked by the embedding model. Never on the share card. */
    val line: String? = null,
)

internal val EmotionGroup.colorName get() = when (this) {
    EmotionGroup.BRIGHT -> "환한 노랑"
    EmotionGroup.CALM -> "평온한 초록"
    EmotionGroup.FLUTTER -> "설레는 분홍"
    EmotionGroup.LOW -> "잔잔한 파랑"
    EmotionGroup.WORN -> "고단한 보라"
}

private fun String.iGa() = this + if (Insights.hasBatchim(this)) "이" else "가"
private fun String.ieotda() = this + if (Insights.hasBatchim(this)) "이었어" else "였어"

private val CHEERS = mapOf(
    EmotionGroup.BRIGHT to listOf("다음 주도 이 빛 그대로 이어 가 보자.", "좋았던 순간, 다음 주에도 꼭 들려줘."),
    EmotionGroup.CALM to listOf("다음 주도 지금처럼 잔잔하길 바랄게.", "편안했던 이번 주, 오래 기억해 둘게."),
    EmotionGroup.FLUTTER to listOf("다음 주엔 또 어떤 일이 두근거리게 할까?", "설레는 마음, 다음 주에도 같이 나누자."),
    EmotionGroup.LOW to listOf("무거웠던 마음, 여기 내려놓고 가도 돼. 다음 주엔 내가 더 따뜻하게 켜 둘게.", "다음 주엔 조금 가벼워지길. 나 계속 여기 있을게."),
    EmotionGroup.WORN to listOf("이번 주 정말 애썼어. 다음 주엔 내가 더 포근하게 켜 둘게.", "고생 많았어. 다음 주도 네 편이야."),
)

/**
 * The letter for the week starting [start] (Monday). [recent] = the last 4 weeks up to that week's end, for one
 * 원인 (a week alone is too few days to say anything).
 */
fun weeklyLetter(start: LocalDate, week: List<Entry>, recent: List<Entry>): WeeklyLetter {
    val end = start.plusDays(6)
    val r = MonthReport.of(week)
    val dom = r.dominant
    val byDay = week.groupBy { it.date }
    val paragraphs = buildList {
        if (week.isEmpty()) {
            add("이번 주는 조용히 지나갔네. 쓰지 않은 주도 괜찮아.")
            add("다음 주에 한 줄이라도 들려주면 반가울 거야.")
            return@buildList
        }
        add("이번 주엔 ${r.days}일 동안 빛을 켜 줬어. 고마워.")
        if (dom != null) {
            val top = r.top.filter { it.first != EmotionKey.UNSURE }
            val tail = when {
                top.size >= 2 -> " 제일 자주 느낀 건 「${top[0].first.label}」(${top[0].second}번), 「${top[1].first.label}」도 ${if (top[1].second > 1) "${top[1].second}번 " else ""}찾아왔어."
                top.size == 1 -> " 제일 자주 느낀 건 「${top[0].first.label}」${if (top[0].second > 1) "(${top[0].second}번)" else ""}이야."
                else -> ""
            }
            add("이번 주 빛은 대체로 ${dom.colorName.ieotda()}.$tail")
        } else add("이번 주는 잘 모르겠는 마음이 많았구나. 그런 주도 있어.")
        Insights.causes(recent, max = 1).firstOrNull()?.let { c ->
            add("요즘 보니까 ${c.label}엔 ${c.group.label.iGa()} 평소보다 ${c.lift}% 더 켜졌어.")
        }
    }
    val cheer = dom?.let { CHEERS.getValue(it).let { l -> l[Math.floorMod(start.toEpochDay().toInt(), l.size)] } } ?: "다음 주에도 불 켜 두고 기다릴게."
    return WeeklyLetter(
        start = start,
        label = if (start.monthValue == end.monthValue) "${start.monthValue}월 ${start.dayOfMonth}일 – ${end.dayOfMonth}일"
        else "${start.monthValue}월 ${start.dayOfMonth}일 – ${end.monthValue}월 ${end.dayOfMonth}일",
        light = if (week.isEmpty()) LightMixer.OFF else LightMixer.mix(week.flatMap { it.emotions }),
        dominant = dom, days = r.days,
        dayLights = (0..6).map { i -> byDay[start.plusDays(i.toLong()).toString()]?.let { l -> LightMixer.mixDay(l.map { it.emotions }) } },
        top = r.top, paragraphs = paragraphs, cheer = cheer,
    )
}

@HiltViewModel
class WeeklyLetterViewModel @Inject constructor(saved: SavedStateHandle, repository: EntryRepository, private val ai: ReportAi) : ViewModel() {
    private val offset = saved.toRoute<WeeklyLetterRoute>().offset
    private val memo = AiMemo<String?>()

    val letter = repository.all().transformLatest { all ->
        val start = weekStart(LocalDate.now()).plusWeeks(offset.toLong())
        fun range(from: LocalDate) = all.filter { LocalDate.parse(it.date).let { d -> !d.isBefore(from) && !d.isAfter(start.plusDays(6)) } }
        val week = range(start)
        val base = weeklyLetter(start, week, range(start.minusWeeks(3)))
        emit(base)
        if (week.isNotEmpty()) emit(base.copy(line = memo.get(week) { runCatching { ai.centralSentence(week.map { it.body }) }.getOrNull() }))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun WeeklyLetterScreen(onBack: () -> Unit, onPremium: () -> Unit, vm: WeeklyLetterViewModel = hiltViewModel()) {
    val c = Od.c
    val l = vm.letter.collectAsStateWithLifecycle().value
    var sharing by rememberSaveable { mutableStateOf(false) }
    val glow = l?.dominant?.glowColor ?: OdColors.BrandGlow
    Box(Modifier.fillMaxSize().background(c.bg).ambient(glow, 0.9f, 170.dp, 420.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            OdTopBar(title = "주간 온이 편지", subtitle = l?.label, left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            if (l == null) return@Column
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OniFigure(if (l.days == 0) LightMixer.OFF else l.light, if (l.days == 0) OniExpression.SLEEP else OniExpression.LETTER, 130.dp)
                OdCard(Modifier.fillMaxWidth().padding(top = 4.dp), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 22.dp)) {
                    Text("이번 주의 빛", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                    WeekDots(l.dayLights, Modifier.padding(top = 10.dp, bottom = 6.dp), c.ink2, c.surface2)
                    l.paragraphs.forEach { Text(it, style = OdType.reply.copy(fontSize = 15.5.sp, lineHeight = 26.sp), modifier = Modifier.padding(top = 12.dp)) }
                    l.line?.let { line ->
                        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("이번 주의 한 줄", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2, modifier = Modifier.weight(1f))
                                AiBadge("AI가 골랐어요", icon = true)
                            }
                            Text("“$line”", style = OdType.diary, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Text(l.cheer, style = OdType.reply.copy(fontSize = 15.5.sp, lineHeight = 26.sp), modifier = Modifier.padding(top = 14.dp))
                    Text("— 온이가", style = OdType.caption1, color = c.ink2, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text("온이의 한마디 · 내 기록으로 폰 안에서 만들었어요", style = OdType.caption2, color = c.ink3, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            if (l.days > 0) OdButton("카드로 공유하기", { sharing = true }, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), icon = OdIcon.Share)
        }
    }
    if (sharing && l != null) StoryShareDialog("주간 온이 편지", "onee_week_${l.start}", glow, { sharing = false }, onPremium, "이번 주 빛을 한 장에 담았어요") { theme -> LetterCard(l, theme) }
}

/** 월..일 orbs; an unlit day is a faint ring. */
@Composable
internal fun WeekDots(lights: List<Light?>, modifier: Modifier, label: Color, off: Color, size: androidx.compose.ui.unit.Dp = 26.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        lights.forEachIndexed { i, light ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (light == null || light.off) Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(off))
                else LightOrb(rgb(light.light), rgb(light.glow), size)
                Text("월화수목금토일"[i].toString(), style = OdType.caption2.copy(fontSize = 10.sp), color = label, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun LetterCard(l: WeeklyLetter, theme: CardTheme) {
    val ink = theme.ink
    Column {
        Text("${l.label} · 주간 온이 편지", style = OdType.caption2.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = 0.7f))
        val name = l.dominant?.colorName
        Text(
            if (name == null) "이번 주는\n잔잔히 쉬어 갔어요" else "이번 주 나는\n${name}${if (Insights.hasBatchim(name)) "이었어요" else "였어요"}",
            style = OdType.title1.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold), color = ink, modifier = Modifier.padding(top = 4.dp),
        )
        WeekDots(l.dayLights, Modifier.padding(top = 22.dp), ink.copy(alpha = 0.6f), theme.offCell, size = 22.dp)
        val top = l.top.filter { it.first != EmotionKey.UNSURE }.take(3)
        if (top.isNotEmpty()) Text(
            "자주 느낀 감정\n" + top.joinToString(" · ") { it.first.label },
            style = OdType.caption1.copy(fontSize = 12.sp, lineHeight = 18.sp), color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 20.dp),
        )
        Row(Modifier.padding(top = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Oni(l.light.takeUnless { l.days == 0 }, OniExpression.LETTER, Modifier.size(78.dp), animate = false)
            Text(l.cheer, style = OdType.caption1.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium), color = ink, modifier = Modifier.width(120.dp))
        }
    }
}
