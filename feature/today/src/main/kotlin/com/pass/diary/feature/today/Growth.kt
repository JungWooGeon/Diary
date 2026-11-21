package com.pass.diary.feature.today

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.billing.CardTheme
import com.pass.diary.core.billing.NudgeGate
import com.pass.diary.core.billing.NudgeReason
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.Insights
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.LightOrb
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.Oni
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.rgb
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.light.Palette
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Gating
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** core:ai behind a seam (bound in :app): the crisis rules only. */
interface TodayAi {
    suspend fun isCrisis(text: String): Boolean
}

/** 오늘 tab extras (AI_GROWTH E9 E11, 빛 수집, 결제를 권하는 순간). */
data class Growth(
    /** E9 마음 예보 for today, or null. */
    val forecast: Insights.Forecast?,
    /** E11: heavy days in a row (≥ Insights.CHECK_IN_DAYS shows the card), 0 once dismissed today. */
    val heavy: Int,
    /** A recent entry hit the crisis rules: the help card comes first, nothing is sold. */
    val crisis: Boolean,
    val streak: Int,
    /** 빛 수집: a reward reached and not celebrated yet. */
    val newReward: Gating.Reward?,
    /** A rewarded palette trial that ended (its end time) and hasn't been followed up. */
    val trialEnded: Long?,
)

@HiltViewModel
class GrowthViewModel @Inject constructor(
    @ApplicationContext context: Context,
    repository: EntryRepository,
    settings: SettingsRepository,
    private val ai: TodayAi,
    val nudges: NudgeGate,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("growth", Context.MODE_PRIVATE)
    private val seen = MutableStateFlow(prefs.getStringSet(SEEN, emptySet())!!.toSet())
    @Volatile private var reached = emptySet<String>()
    private val dismissed = MutableStateFlow(prefs.getLong(CHECK_IN_DAY, Long.MIN_VALUE))

    @OptIn(ExperimentalCoroutinesApi::class)
    val growth = combine(repository.observeRange("0000-01-01", "9999-12-31"), settings.settings, seen, dismissed) { all, s, seenNow, off ->
        val today = LocalDate.now()
        val heavyRun = Insights.heavyRun(all, today)
        val recent = all.filter { !LocalDate.parse(it.date).isBefore(today.minusDays(6)) }
        val progress = Gating.Progress.of(all.map { it.date })
        reached = Gating.REWARDS.filter { it.reached(progress) }.map { it.palette.key }.toSet()
        Triple(recent, heavyRun, Growth(
            forecast = Insights.forecast(all, today),
            heavy = if (off == today.toEpochDay()) 0 else heavyRun,
            crisis = false,
            streak = Gating.streak(all.map { LocalDate.parse(it.date) }.toSet(), today),
            newReward = Gating.REWARDS.lastOrNull { it.reached(progress) && it.palette.key !in seenNow }, // one dialog: the newest
            trialEnded = s.paletteTrialUntil.takeIf { s.paletteTrial != null && it in 1 until System.currentTimeMillis() },
        ))
    }.mapLatest { (recent, heavyRun, g) ->
        // Crisis rules over the last week's entries whenever the mood ran heavy (the check-in would show).
        if (heavyRun < Insights.CHECK_IN_DAYS) g else g.copy(crisis = recent.any { runCatching { ai.isCrisis(it.text()) }.getOrDefault(false) })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun celebrated(r: Gating.Reward) {
        seen.value = seen.value + reached + r.palette.key // everything reached so far counts as celebrated
        prefs.edit().putStringSet(SEEN, seen.value).apply()
    }

    fun dismissCheckIn() {
        dismissed.value = LocalDate.now().toEpochDay()
        prefs.edit().putLong(CHECK_IN_DAY, dismissed.value).apply()
    }

    private fun Entry.text() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")

    private companion object {
        const val SEEN = "seen_rewards"
        const val CHECK_IN_DAY = "check_in_dismissed"
    }
}

/** E9: one line under the hero on the forecast day. */
@Composable
internal fun ForecastCard(f: Insights.Forecast) {
    val c = Od.c
    OdCard(Modifier.fillMaxWidth(), flat = true, padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LightOrb(f.group.light.let(::rgb), f.group.glow.let(::rgb), 28.dp)
            Column(Modifier.weight(1f)) {
                Text("마음 예보", style = OdType.caption2.copy(fontWeight = FontWeight.Bold), color = c.ink2)
                Text(f.line, style = OdType.body2, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** E11 마음 체크인: a gentle "how are you", never a diagnosis or advice. With crisis wording, the help card instead. */
@Composable
internal fun CheckInCard(crisis: Boolean, onTalk: () -> Unit, onDismiss: () -> Unit) {
    if (crisis) return CrisisCard()
    val c = Od.c
    OdCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Oni(null, OniExpression.COMFORT, Modifier.size(56.dp), animate = false)
            Column(Modifier.weight(1f)) {
                Text("요 며칠 마음이 무거웠지.", style = OdType.title3)
                Text("오늘은 좀 어때? 말하고 싶을 때 들려줘. 아니어도 괜찮아.", style = OdType.body2, color = c.ink2, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdButton("얘기하러 가기", onTalk, Modifier.weight(1f), small = true, height = 40.dp)
            OdButton("괜찮아, 고마워", onDismiss, Modifier.weight(1f), kind = ButtonKind.Secondary, small = true, height = 40.dp)
        }
    }
}

/**
 * 위기 안내 (PLAN §3.2): 109 · 1577-0199, same wording as feature/write's CrisisCard.
 * ponytail: a copy so the write screens (another work stream) stay untouched; merge into core:designsystem with that one.
 */
@Composable
private fun CrisisCard() {
    val c = Od.c
    val context = LocalContext.current
    fun dial(n: String) = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
    OdCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp)) {
        Text("요즘 정말 많이 무거웠구나. 혼자 다 견디지 않아도 돼. 지금 이 마음을 같이 들어 줄 사람이 있어.", style = OdType.reply)
        Text("— 온이가", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 10.dp))
        Divider(Modifier.padding(top = 16.dp, bottom = 6.dp))
        CallRow("109", "자살예방 상담 · 24시간") { dial("109") }
        CallRow("1577-0199", "정신건강 위기상담 · 24시간") { dial("15770199") }
        Text("문자·카카오톡 상담도 109 누리집에서 할 수 있어요.", style = OdType.caption2, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun CallRow(number: String, desc: String, onCall: () -> Unit) {
    val c = Od.c
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF7F9FD1).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { OdIcon(OdIcon.Phone, size = 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(number, style = OdType.title3.copy(fontSize = 16.sp))
            Text(desc, style = OdType.caption2, color = c.ink2)
        }
        OdButton("전화", onCall, small = true, height = 36.dp, modifier = Modifier.width(64.dp))
    }
}

/** 빛 수집: Oni cheers in the new palette's light. */
@Composable
internal fun RewardDialog(r: Gating.Reward, onSee: () -> Unit, onClose: () -> Unit) {
    val c = Od.c
    val p = Palette[r.palette]
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.surface,
        icon = { OniFigure(null, OniExpression.CHEER, 120.dp) },
        title = { Text("새 빛을 모았어!", style = OdType.title2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("${r.condition} 선물로 빛 팔레트 「${r.palette.label}」을 받았어요.", style = OdType.body2, color = c.ink2, textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.glows.forEach { col -> Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF000000 or col.toLong()))) }
                }
            }
        },
        confirmButton = { TextButton(onSee) { Text("꾸미기에서 써 보기", style = OdType.buttonSm, color = c.ink) } },
        dismissButton = { TextButton(onClose) { Text("나중에", style = OdType.buttonSm, color = c.ink2) } },
    )
}

/** 오늘의 빛 share card: today's lights in order, no diary text. */
@Composable
internal fun TodayCard(date: LocalDate, entries: List<Entry>, light: Light, theme: CardTheme) {
    val ink = theme.ink
    val g = LightMixer.dominantGroup(entries.flatMap { it.emotions })
    Column {
        Text("${date.year}.${date.monthValue}.${date.dayOfMonth} · 오늘의 빛", style = OdType.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = 0.7f))
        Text(
            if (g == null) "오늘은\n잘 모르겠는 날" else "오늘 나는\n${g.label}${if (Insights.hasBatchim(g.label)) "이었어요" else "였어요"}",
            style = OdType.title1.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold), color = ink, modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            Oni(light, OniExpression.forGroup(g).takeUnless { it == OniExpression.SLEEP } ?: OniExpression.DEFAULT, Modifier.size(150.dp), animate = false)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            entries.takeLast(6).forEach { e ->
                val l = LightMixer.mix(e.emotions)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (l.off) Box(Modifier.size(20.dp).clip(CircleShape).background(theme.offCell)) else LightOrb(rgb(l.light), rgb(l.glow), 20.dp)
                    Text(e.time, style = OdType.caption2.copy(fontSize = 9.sp), color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        val names = entries.flatMap { it.emotions }.map { it.key.label }.distinct().take(4)
        if (names.isNotEmpty()) Text(names.joinToString(" · "), style = OdType.caption1.copy(fontSize = 12.sp), color = ink.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

/** Which suggestion (if any) the 오늘 screen may show now. Never next to a heavy mood or a crisis. */
internal fun nudgeFor(g: Growth, premium: Boolean, gate: NudgeGate): Pair<NudgeReason, String>? {
    if (premium || g.crisis || g.heavy >= Insights.CHECK_IN_DAYS || g.newReward != null) return null
    g.trialEnded?.let { val k = "trial_$it"; if (gate.canShow(k)) return NudgeReason.TRIAL_END to k }
    if (g.streak >= 7 && gate.canShow("streak7")) return NudgeReason.STREAK7 to "streak7"
    return null
}
