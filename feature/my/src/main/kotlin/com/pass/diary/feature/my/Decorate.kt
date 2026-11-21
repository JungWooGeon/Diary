package com.pass.diary.feature.my

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pass.diary.core.billing.LocalAdManager
import com.pass.diary.core.billing.PremiumState
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.designsystem.CardHeader
import com.pass.diary.core.designsystem.IconBtn
import com.pass.diary.core.designsystem.ListRow
import com.pass.diary.core.designsystem.Od
import com.pass.diary.core.designsystem.OdCard
import com.pass.diary.core.designsystem.OdColors
import com.pass.diary.core.designsystem.OdIcon
import com.pass.diary.core.designsystem.OdToggle
import com.pass.diary.core.designsystem.OdTopBar
import com.pass.diary.core.designsystem.OdType
import com.pass.diary.core.designsystem.PremiumBadge
import com.pass.diary.core.designsystem.family
import com.pass.diary.core.light.Palette
import com.pass.diary.core.model.FontId
import com.pass.diary.core.model.Gating
import com.pass.diary.core.model.LightPaletteId
import com.pass.diary.core.model.Settings
import com.pass.diary.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class DecorateViewModel @Inject constructor(
    private val repo: SettingsRepository,
    premium: PremiumState,
    entries: EntryRepository,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val premium = premium.isPremium.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val progress = entries.observeRange("0000-01-01", "9999-12-31").map { l -> Gating.Progress.of(l.map { it.date }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gating.Progress())

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setThemeMode(m) }
    fun setPalette(p: LightPaletteId) = viewModelScope.launch { repo.setPalette(p) }
    fun setFont(f: FontId) = viewModelScope.launch { repo.setFont(f) }
    fun setTextSize(step: Int) = viewModelScope.launch { repo.setTextSize(step) }
    /** "광고 보고 하루 써 보기": until local midnight. */
    fun startTrial(p: LightPaletteId) = viewModelScope.launch {
        repo.startPaletteTrial(p, LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ======================================================================= 꾸미기

@Composable
fun DecorateScreen(onBack: () -> Unit, onPremium: () -> Unit, vm: DecorateViewModel = hiltViewModel()) {
    val c = Od.c
    val ctx = LocalContext.current
    val activity = LocalActivity.current
    val ads = LocalAdManager.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val premium by vm.premium.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var offer by remember { mutableStateOf<LightPaletteId?>(null) }
    LaunchedEffect(ads, premium) { if (!premium) ads?.loadRewarded(ctx) }
    val now = System.currentTimeMillis()
    val shown = Gating.effective(settings, premium, progress, now)
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().verticalScroll(rememberScrollState())) {
        OdTopBar(title = "꾸미기", left = { IconBtn(OdIcon.Back, onBack, "뒤로") })
        Column(Modifier.padding(horizontal = 20.dp)) {
            Section("테마")
            val sel = when (settings.themeMode) { ThemeMode.LIGHT -> 0; ThemeMode.DARK -> 1; ThemeMode.PAPER -> 2; ThemeMode.SYSTEM -> if (c.dark) 1 else 0 }
            val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.PAPER)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("라이트", Color(0xFFFBF7F1), Color(0xFF2E2622)), Triple("다크", Color(0xFF1C1830), Color(0xFFF4EFE8)), Triple("종이", Color(0xFFF3EBDD), Color(0xFF3A2E25)))
                    .forEachIndexed { i, (name, bg, fg) ->
                        Column(Modifier.weight(1f).clickable { vm.setTheme(modes[i]) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier.fillMaxWidth().height(110.dp).clip(shape).background(bg).border(if (i == sel) 2.5.dp else 1.dp, if (i == sel) c.ink else c.line, shape).padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFFDF6), OdColors.BrandGlow, OdColors.BrandGlow.copy(alpha = 0f)))))
                                Bar(0.6f, fg.copy(alpha = 0.7f)); Bar(0.8f, fg.copy(alpha = 0.25f)); Bar(0.7f, fg.copy(alpha = 0.25f))
                            }
                            Text(name, style = OdType.body2.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp))
                        }
                    }
            }
            ListRow(null, "시스템 설정 따르기", height = 48.dp, trailing = { OdToggle(settings.themeMode == ThemeMode.SYSTEM, { vm.setTheme(if (it) ThemeMode.SYSTEM else if (c.dark) ThemeMode.DARK else ThemeMode.LIGHT) }) })

            Section("빛 팔레트", "기록으로 모으는 빛 · 특별한 빛", top = 16.dp)
            // 3 per row: 6 palettes no longer fit one row on a phone.
            LightPaletteId.entries.chunked(3).forEachIndexed { row, ids -> Row(Modifier.padding(top = if (row == 0) 0.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ids.forEach { id ->
                    // Swatches are drawn raw (not through the active palette): each shows its own colors.
                    val cols = if (id == LightPaletteId.BASIC) listOf(0xF5A957) + Palette[id].glows.take(4) else Palette[id].glows
                    val unlocked = Gating.paletteUnlocked(id, premium, progress, settings.paletteTrial, settings.paletteTrialUntil, now)
                    val reward = Gating.reward(id)
                    Column(
                        Modifier.weight(1f).clickable {
                            when {
                                unlocked -> vm.setPalette(id)
                                reward != null -> toast(ctx, "${reward.condition}이면 받아요 · 지금 ${reward.progress(progress)}/${reward.need}")
                                else -> offer = id
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            val shape = RoundedCornerShape(14.dp)
                            Row(
                                Modifier.fillMaxWidth().height(56.dp).let { if (id == shown.palette) it.border(2.5.dp, c.ink, shape) else it.cardShadowSmall().border(1.dp, c.line, shape) }
                                    .clip(shape).background(c.surface),
                                horizontalArrangement = Arrangement.spacedBy((-4).dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                cols.forEach { col ->
                                    Box(Modifier.size(17.dp).clip(CircleShape).background(c.surface).padding(1.dp).clip(CircleShape).background(Color(0xFF000000 or col.toLong())))
                                }
                            }
                            if (id.premium && !premium) PremiumBadge(null, Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-7).dp), height = 20.dp)
                            else if (!unlocked) Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-7).dp).size(20.dp).clip(CircleShape).background(c.surface2),
                                contentAlignment = Alignment.Center,
                            ) { OdIcon(OdIcon.Lock, size = 12.dp, tint = c.ink2) }
                        }
                        val trial = id.premium && !premium && unlocked
                        Text(if (trial) "${id.label} · 오늘만" else id.label, style = OdType.caption2.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                    }
                }
            } }

            // 빛 수집 (PLAN §3.3): what recording earns, with progress. Premium opens these too.
            Section("빛 수집", "기록할수록 모여요", top = 22.dp)
            OdCard(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                Gating.REWARDS.forEachIndexed { i, r ->
                    if (i > 0) com.pass.diary.core.designsystem.Divider()
                    val done = r.reached(progress)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                            Palette[r.palette].glows.take(3).forEach { col -> Box(Modifier.size(18.dp).clip(CircleShape).background(c.surface).padding(1.dp).clip(CircleShape).background(Color(0xFF000000 or col.toLong()))) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("「${r.palette.label}」", style = OdType.body2.copy(fontWeight = FontWeight.SemiBold))
                            Text(r.condition, style = OdType.caption2, color = c.ink2)
                            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.surface2)) {
                                Box(Modifier.fillMaxWidth(r.progress(progress) / r.need.toFloat()).height(5.dp).clip(RoundedCornerShape(3.dp)).background(OdColors.BrandGlow))
                            }
                        }
                        Text(if (done) "모았어요" else "${r.progress(progress)}/${r.need}", style = OdType.caption1.copy(fontWeight = FontWeight.Bold), color = if (done) c.ink else c.ink2)
                    }
                }
                Text("연속 기록은 하루쯤 빠져도 이어져요 (일주일에 한 번).", style = OdType.caption2, color = c.ink3, modifier = Modifier.padding(bottom = 8.dp))
            }

            Section("글꼴", top = 24.dp)
            OdCard(padding = PaddingValues(horizontal = 18.dp)) {
                FontId.entries.forEach { f ->
                    val locked = !Gating.fontUnlocked(f, premium)
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).clickable { if (locked) onPremium() else vm.setFont(f) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val st = OdType.body1.copy(fontFamily = f.family(), fontWeight = FontWeight.Medium, fontSize = if (f == FontId.HAND) 18.sp else 15.sp)
                        Text("오늘 하루 어땠어?", style = st, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(f.label, style = OdType.caption2, color = c.ink2)
                        if (locked) PremiumBadge(null)
                        val on = f == shown.font
                        val m = Modifier.size(22.dp).clip(CircleShape)
                        Box(if (on) m.background(c.primary) else m.border(1.5.dp, c.ink3, CircleShape), contentAlignment = Alignment.Center) {
                            if (on) OdIcon(OdIcon.Check, size = 13.dp, stroke = 2.6f, tint = c.onPrimary)
                        }
                    }
                }
            }

            Section("글자 크기", listOf("작게", "조금 작게", "보통", "조금 크게", "크게")[settings.textSize], top = 22.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("가", style = OdType.caption1.copy(fontWeight = FontWeight.Bold))
                SliderTrack(settings.textSize / 4f, { vm.setTextSize((it * 4).roundToInt()) }, Modifier.weight(1f))
                Text("가", style = OdType.title1.copy(fontSize = 20.sp))
            }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }
    offer?.let { p ->
        // PLAN §4: the one rewarded ad — "광고 보고 특별 빛 팔레트 하루 써 보기". Never blocks: no fill = free trial anyway.
        AlertDialog(
            onDismissRequest = { offer = null },
            containerColor = c.surface,
            title = { Text("「${p.label}」 하루 써 볼까요?", style = OdType.title3) },
            text = { Text("짧은 광고를 보면 오늘 밤 12시까지 써 볼 수 있어요. 프리미엄이면 언제든 쓸 수 있어요.", style = OdType.body2, color = c.ink2) },
            confirmButton = {
                TextButton({
                    offer = null
                    val start = { vm.startTrial(p); toast(ctx, "오늘 하루 「${p.label}」 빛으로 켜 둘게요") }
                    ads?.showRewarded(activity, start) ?: start()
                }) { Text("광고 보고 써 보기", style = OdType.buttonSm, color = c.ink) }
            },
            dismissButton = { TextButton({ offer = null; onPremium() }) { Text("프리미엄 보기", style = OdType.buttonSm, color = c.ink2) } },
        )
    }
}

@Composable
private fun Modifier.cardShadowSmall(): Modifier =
    if (Od.c.dark) this else this.dropShadow(RoundedCornerShape(14.dp), Shadow(radius = 20.dp, color = Color(0x0F2E2622), offset = DpOffset(0.dp, 6.dp)))

@Composable
private fun ColumnScope.Bar(w: Float, color: Color) = Box(Modifier.fillMaxWidth(w).height(5.dp).clip(RoundedCornerShape(3.dp)).background(color))

@Composable
private fun Section(title: String, right: String? = null, top: Dp = 4.dp) = CardHeader(title, right, Modifier.padding(top = top, bottom = 10.dp))

/** 5-step slider: 6dp track, ink fill, 24dp white knob. */
@Composable
private fun SliderTrack(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val c = Od.c
    var width by remember { mutableIntStateOf(1) }
    val snap = { x: Float -> ((x / width).coerceIn(0f, 1f) * 4).roundToInt() / 4f }
    Box(
        modifier.height(28.dp)
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(snap(down.position.x))
                    drag(down.id) { onChange(snap(it.position.x)) }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.surface2))
        Box(Modifier.fillMaxWidth(value).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.ink))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(
                Modifier.offset(x = maxWidth * value - 12.dp).size(24.dp)
                    .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color(0x40000000), offset = DpOffset(0.dp, 1.dp)))
                    .clip(CircleShape).background(Color.White),
            )
        }
    }
}
