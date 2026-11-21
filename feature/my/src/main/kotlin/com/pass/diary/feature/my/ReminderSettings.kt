package com.pass.diary.feature.my

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pass.diary.core.designsystem.ButtonKind
import com.pass.diary.core.designsystem.Divider
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.ListRow
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdButton
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdToggle
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.OniExpression
import com.pass.diary.core.designsystem.OniFigure
import com.pass.diary.core.designsystem.ambient
import com.pass.diary.core.reminder.ReminderLogic
import com.pass.diary.core.reminder.ReminderPrefs
import com.pass.diary.core.reminder.Reminders
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

@Serializable data object ReminderSettingsRoute

/** 마이 row value: "매일 오후 10:00" / "꺼짐". */
@Composable
fun reminderValue(): String {
    val p by Reminders.prefs(LocalContext.current).collectAsStateWithLifecycle(null)
    val r = p ?: return ""
    return if (r.enabled) "${ReminderLogic.daysLabel(r.days)} ${ReminderLogic.timeLabel(r.hour, r.minute)}" else "꺼짐"
}

/** 마이 › 알림: 기록 알림 (시간·요일·이미 썼으면 쉬기) + 1년 전 오늘 알림. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit) {
    val c = Od.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = Reminders.prefs(ctx).collectAsStateWithLifecycle(null).value ?: return
    fun set(change: (ReminderPrefs) -> ReminderPrefs) { scope.launch { Reminders.update(ctx, change) } }
    BackHandler(onBack = onBack)

    // Re-checked on resume: the user may come back from system settings having allowed notifications.
    var allowed by remember { mutableStateOf(Reminders.canNotify(ctx)) }
    LifecycleResumeEffect(Unit) { allowed = Reminders.canNotify(ctx); onPauseOrDispose {} }
    var pending by remember { mutableStateOf<((ReminderPrefs) -> ReminderPrefs)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = Reminders.canNotify(ctx)
        if (ok) pending?.let(::set) else denied = true
        pending = null
    }
    /** Turning something on asks for POST_NOTIFICATIONS first (13+); turning off never asks. */
    fun turnOn(change: (ReminderPrefs) -> ReminderPrefs) {
        when {
            Reminders.needsPermission(ctx) -> { pending = change; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
            else -> { set(change); if (!Reminders.canNotify(ctx)) denied = true }
        }
    }
    var picking by remember { mutableStateOf(false) }
    // E12: when the user usually starts writing, learned from their own entries on the phone.
    val usual by produceState<java.time.LocalTime?>(null) { value = runCatching { Reminders.usualTime(ctx) }.getOrNull() }
    if (picking) {
        val state = rememberTimePickerState(p.hour, p.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("언제 불러 줄까요?", style = OdType.title3) },
            text = { TimePicker(state) },
            // The user's own time always wins over the learned one (E12).
            confirmButton = { TextButton(onClick = { picking = false; set { it.copy(hour = state.hour, minute = state.minute, auto = false) } }) { Text("확인", color = c.ink, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소", color = c.ink2) } },
            containerColor = c.surface,
        )
    }

    Box(Modifier.fillMaxSize().background(c.bg).ambient(OdColors.Dusk, 0.6f, centerY = 140.dp, radius = 380.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            OdTopBar(title = "알림", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OniFigure(null, if (p.enabled) OniExpression.DEFAULT else OniExpression.SLEEP, 96.dp)
                    Column(Modifier.weight(1f)) {
                        Text("정한 시간에\n온이가 불러 줘요", style = OdType.title2)
                        Text("매번 다른 말로 살짝 불러요. 쓰지 않는 날이 있어도 괜찮아요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if ((p.enabled || p.yearAgo || denied) && !allowed) DeniedCard {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    ToggleRow("기록 알림", "하루 끝에 온이가 오늘 얘기를 물어봐요", p.enabled) { on ->
                        if (on) turnOn { it.copy(enabled = true) } else set { it.copy(enabled = false) }
                    }
                    if (p.enabled) {
                        Divider()
                        val u = usual
                        ListRow(
                            null, "시간",
                            if (p.auto && u != null) "${ReminderLogic.timeLabel(u.hour, u.minute)} · 자동" else ReminderLogic.timeLabel(p.hour, p.minute),
                            { picking = true }, height = 52.dp,
                        )
                        Divider()
                        ToggleRow(
                            "주로 쓰는 시간에 맞추기",
                            when {
                                u == null -> "일주일쯤 기록하면 주로 쓰는 시간을 알려 줄게요"
                                else -> "요즘은 주로 ${ReminderLogic.timeLabel(u.hour, u.minute)}쯤 써요. 시간을 직접 고르면 그 시간이 우선이에요"
                            },
                            p.auto,
                        ) { on -> set { it.copy(auto = on) } }
                        if (!p.auto && u != null && kotlin.math.abs((u.hour * 60 + u.minute) - (p.hour * 60 + p.minute)) >= 60) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2)
                                    .clickable { set { it.copy(hour = u.hour, minute = u.minute) } }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OdIcon(OdIcon.Sparkle, size = 16.dp)
                                Text("${ReminderLogic.timeLabel(u.hour, u.minute)}으로 바꿔 볼까요?", style = OdType.caption1, modifier = Modifier.weight(1f))
                                Text("바꾸기", style = OdType.buttonSm)
                            }
                        }
                        Divider()
                        Text("요일", style = OdType.body2.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { d ->
                                val on = d in p.days
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape).background(if (on) c.primary else c.surface2)
                                        // At least one day stays on; "끄기" is the switch above.
                                        .clickable(enabled = !on || p.days.size > 1) { set { it.copy(days = if (on) it.days - d else it.days + d) } },
                                    contentAlignment = Alignment.Center,
                                ) { Text(ReminderLogic.DAY_NAMES[d.ordinal], style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = if (on) c.onPrimary else c.ink2) }
                            }
                        }
                        Divider()
                        ToggleRow("오늘 이미 썼으면 쉬기", "그날 기록이 있으면 알림을 보내지 않아요", p.skipIfWritten) { on -> set { it.copy(skipIfWritten = on) } }
                    }
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    ToggleRow("주간 온이 편지", "일요일 밤, 한 주의 빛을 모은 편지가 도착해요", p.weekly) { on ->
                        if (on) turnOn { it.copy(weekly = true) } else set { it.copy(weekly = false) }
                    }
                    Divider()
                    ToggleRow("마음 예보", "요일마다 반복되는 흐름이 보이면 그날 낮에 살짝 알려 줘요 (일주일에 한 번까지)", p.forecast) { on ->
                        if (on) turnOn { it.copy(forecast = true) } else set { it.copy(forecast = false) }
                    }
                }
                OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                    ToggleRow("1년 전 오늘 알림", "1년 전 오늘 쓴 기록이 있으면 같은 시간에 알려 줘요", p.yearAgo) { on ->
                        if (on) turnOn { it.copy(yearAgo = true) } else set { it.copy(yearAgo = false) }
                    }
                }
                OdCard(flat = true, padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OdIcon(OdIcon.Lock, size = 18.dp)
                        Text("알림에는 일기 내용이 나오지 않아요", style = OdType.title3)
                    }
                    Text("잠금을 켜 두지 않아도, 알림 문구에는 일기 내용이나 기분이 들어가지 않아요. 정확한 시각보다 몇 분 늦게 올 수 있어요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun DeniedCard(onSettings: () -> Unit) {
    val c = Od.c
    OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OdIcon(OdIcon.Bell, size = 18.dp)
            Text("알림이 꺼져 있어요", style = OdType.title3)
        }
        Text("괜찮아요. 알림 없이도 언제든 와서 쓰면 돼요. 온이가 불러 주길 원하면 휴대폰 설정에서 알림을 허용해 주세요.", style = OdType.caption1, color = c.ink2, modifier = Modifier.padding(top = 8.dp))
        OdButton("알림 설정 열기", onSettings, Modifier.padding(top = 12.dp), kind = ButtonKind.Secondary, small = true, height = 38.dp)
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = OdType.body2.copy(fontWeight = FontWeight.SemiBold))
            Text(desc, style = OdType.caption2, color = Od.c.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        OdToggle(on, onChange)
    }
}
