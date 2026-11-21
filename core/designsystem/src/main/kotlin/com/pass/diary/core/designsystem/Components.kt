package com.pass.diary.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion

// ---------------------------------------------------------------- surfaces

/** Card elevation (README §4): light = two soft shadows, dark = no shadow + 1dp white 4 % hairline. */
@Composable
fun Modifier.odCard(shape: Shape = RoundedCornerShape(20.dp), color: Color = Od.c.surface): Modifier {
    val c = Od.c
    val base = if (c.dark) this.border(1.dp, Color.White.copy(alpha = 0.04f), shape)
    else this
        .dropShadow(shape, Shadow(radius = 2.dp, color = Color(0x0A2E2622), offset = DpOffset(0.dp, 1.dp)))
        .dropShadow(shape, Shadow(radius = 20.dp, color = Color(0x0F2E2622), offset = DpOffset(0.dp, 6.dp)))
    return base.clip(shape).background(color)
}

@Composable
fun OdCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    flat: Boolean = false,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Od.c
    val m = if (flat) modifier.clip(RoundedCornerShape(20.dp)).background(color ?: c.surface2) else modifier.odCard(color = color ?: c.surface)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(padding), content = content)
}

/** "제목 ……… 오른쪽 보조" header used at the top of cards. */
@Composable
fun CardHeader(title: String, right: String? = null, modifier: Modifier = Modifier, rightStyle: TextStyle = OdType.caption1) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = OdType.title3, modifier = Modifier.weight(1f))
        if (right != null) Text(right, style = rightStyle, color = Od.c.ink2)
    }
}

/** Screen ambient (README §2): radial glow 420×336 around Oni, α light .30 / dark .34 × [strength]. Cross-fades 0.56 s. */
@Composable
fun Modifier.ambient(glow: Color, strength: Float = 1f, centerY: Dp = 190.dp, radius: Dp = 460.dp): Modifier {
    val c by animateColorAsState(glow, tween(560), label = "ambient")
    val a = (if (Od.c.dark) 0.34f else 0.30f) * strength
    return drawBehind {
        val rx = radius.toPx() / 2f
        val center = Offset(size.width / 2f, centerY.toPx())
        // Ellipse = circle scaled 0.8 vertically around the center.
        scale(1f, 0.8f, pivot = center) {
            drawCircle(
                Brush.radialGradient(0f to c.copy(alpha = a), 0.45f to c.copy(alpha = a * 0.35f), 0.72f to Color.Transparent, center = center, radius = rx),
                radius = rx, center = center,
            )
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
fun OdTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { left?.invoke() }
        if (title != null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = if (subtitle == null) OdType.title2.copy(fontSize = 17.sp) else OdType.title3.copy(fontSize = 16.sp))
            if (subtitle != null) Text(subtitle, style = OdType.caption2.copy(fontSize = 11.5.sp), color = Od.c.ink2)
        }
        Box(Modifier.align(Alignment.CenterEnd)) { right?.invoke() }
    }
}

@Composable
fun IconBtn(icon: OdIcon, onClick: () -> Unit, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = Od.c.ink, size: Dp = 22.dp, stroke: Float = 1.7f) {
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        OdIcon(icon, size = size, tint = tint, stroke = stroke, contentDescription = contentDescription)
    }
}

/** Large-title header used by tab screens ("리포트", "마이", "2026년 11월"). */
@Composable
fun TabHeader(title: String, modifier: Modifier = Modifier, titleTrailing: (@Composable () -> Unit)? = null, right: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().height(52.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = OdType.title1)
        titleTrailing?.invoke()
        Spacer(Modifier.weight(1f))
        right?.invoke(this)
    }
}

// ---------------------------------------------------------------- buttons

enum class ButtonKind { Primary, Secondary, Ghost }

@Composable
fun OdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    small: Boolean = false,
    icon: OdIcon? = null,
    enabled: Boolean = true,
    height: Dp = if (small) 40.dp else 54.dp,
) {
    val c = Od.c
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.primary to c.onPrimary
        ButtonKind.Secondary -> c.surface2 to c.ink
        ButtonKind.Ghost -> Color.Transparent to c.ink2
    }
    val shape = RoundedCornerShape(if (small) 12.dp else 16.dp)
    Row(
        modifier.height(height).clip(shape).background(if (enabled) bg else bg.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) OdIcon(icon, size = if (small) 18.dp else 19.dp, tint = fg)
        val st = if (small) OdType.buttonSm else OdType.button
        Text(text, style = if (kind == ButtonKind.Ghost) st.copy(fontWeight = FontWeight.SemiBold) else st, color = if (enabled) fg else fg.copy(alpha = 0.6f), maxLines = 1)
    }
}

// ---------------------------------------------------------------- chips, badges, dots

@Composable
fun Dot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) = Box(modifier.size(size).clip(CircleShape).background(color))

/** Neutral pill chip (34dp). [selected] = ink fill (template chips, filters). */
@Composable
fun OdChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    outlined: Boolean = false,
    height: Dp = 34.dp,
    dot: Color? = null,
    trailing: OdIcon? = null,
    style: TextStyle = OdType.chip,
    onClick: (() -> Unit)? = null,
) {
    val c = Od.c
    val shape = RoundedCornerShape(50)
    var m = modifier.height(height).clip(shape)
        .background(if (selected) c.primary else if (outlined) c.surface else c.surface2)
    if (outlined && !selected) m = m.border(1.dp, c.line, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dot != null) Dot(dot)
        Text(text, style = style, color = if (selected) c.onPrimary else c.ink, maxLines = 1)
        if (trailing != null) OdIcon(trailing, size = 14.dp, tint = if (selected) c.onPrimary else c.ink2)
    }
}

/** Lit chip for an emotion/group filter: light fill (dark: glow 32 %) + 1.5dp glow border. */
@Composable
fun LitChip(text: String, group: EmotionGroup?, modifier: Modifier = Modifier, height: Dp = 32.dp, trailing: OdIcon? = null, onClick: (() -> Unit)? = null) {
    val c = Od.c
    val glow = group?.glowColor ?: OdColors.OffGlow
    val light = group?.lightColor ?: OdColors.OffLight
    val shape = RoundedCornerShape(50)
    var m = modifier.height(height).clip(shape).background(if (c.dark) glow.copy(alpha = 0.32f) else light).border(1.5.dp, glow, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(glow)
        Text(text, style = OdType.chip.copy(fontSize = 13.sp), color = if (c.dark) Color.White else OdColors.OnLight)
        if (trailing != null) OdIcon(trailing, size = 12.dp, stroke = 2.4f, tint = if (c.dark) Color.White else OdColors.OnLight)
    }
}

/**
 * Emotion chip of the picker (README §5): 40dp, radius 13. Selected = light fill (dark: glow 32 %), 1.5dp glow border and 5
 * intensity dots underneath.
 */
@Composable
fun EmotionChip(key: EmotionKey, intensity: Int?, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = key.label) {
    val c = Od.c
    val g = key.group
    val glow = g?.glowColor ?: OdColors.OffGlow
    val light = g?.lightColor ?: OdColors.OffLight
    val sel = intensity != null
    val shape = RoundedCornerShape(13.dp)
    val bg = when { !sel -> c.surface; c.dark -> glow.copy(alpha = 0.32f); else -> light }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.6f), label = "chip")
    Column(
        modifier.scale(s).height(40.dp).clip(shape).background(bg)
            .border(if (sel) 1.5.dp else 1.dp, if (sel) glow else c.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label, style = OdType.chip.copy(fontSize = 13.5.sp, lineHeight = 16.sp),
            color = when { !sel -> c.ink; c.dark -> Color.White; else -> OdColors.OnLight }, maxLines = 1,
        )
        if (sel) {
            val dc = if (c.dark) glow else OdColors.OnLight
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                repeat(5) { i -> Dot(dc.copy(alpha = if (i < intensity!!) 1f else 0.22f), 4.dp) }
            }
        }
    }
}

@Composable
fun AiBadge(text: String = "온이", modifier: Modifier = Modifier, icon: Boolean = true) {
    val c = Od.c
    Row(
        modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(c.surface2).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon) OdIcon(OdIcon.Sparkle, size = 12.dp, stroke = 2f, tint = c.ink2)
        Text(text, style = OdType.caption2.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = c.ink2)
    }
}

@Composable
fun PremiumBadge(text: String? = "프리미엄", modifier: Modifier = Modifier, height: Dp = 22.dp) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(Brush.linearGradient(listOf(OdColors.PremiumFrom, OdColors.PremiumTo)))
            .padding(horizontal = if (text == null) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        OdIcon(OdIcon.Crown, size = 12.dp, stroke = 2f, tint = OdColors.PremiumInk)
        if (text != null) Text(text, style = OdType.caption2.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = OdColors.PremiumInk)
    }
}

// ---------------------------------------------------------------- segmented tabs, toggle, list rows

@Composable
fun SegTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Od.c
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (c.dark) c.surface else c.surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(10.dp)
            var m = Modifier.weight(1f).height(34.dp)
            if (on) m = if (c.dark) m.clip(shape).background(c.surface2).border(1.dp, c.line, shape)
            else m.dropShadow(shape, Shadow(radius = 3.dp, color = Color(0x14000000), offset = DpOffset(0.dp, 1.dp))).clip(shape).background(c.surface)
            Box(m.clip(shape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                Text(label, style = OdType.chip, color = if (on) c.ink else c.ink2)
            }
        }
    }
}

@Composable
fun OdToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Od.c
    val bg by animateColorAsState(if (checked) c.ok else c.ink3, tween(200), label = "toggle")
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "knob")
    Box(modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(bg).clickable { onChange(!checked) }) {
        Box(Modifier.offset(x = 3.dp + 18.dp * x, y = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** 56dp settings row: icon, label, value, chevron. */
@Composable
fun ListRow(icon: OdIcon?, label: String, value: String? = null, onClick: (() -> Unit)? = null, height: Dp = 56.dp, trailing: (@Composable () -> Unit)? = null) {
    val c = Od.c
    val on = LocalSelectedRow.current == label
    Row(
        Modifier.fillMaxWidth().height(height)
            // Selected (tablet 마이: shown in the detail pane): a soft pill reaching into the card's padding, no layout shift.
            .let { if (on) it.drawBehind { val o = 10.dp.toPx(); val v = 5.dp.toPx(); drawRoundRect(c.surface2, Offset(-o, v), Size(size.width + 2 * o, size.height - 2 * v), CornerRadius(12.dp.toPx())) } else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) OdIcon(icon, size = 22.dp)
        Text(label, style = OdType.body1.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium), modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = OdType.caption1, color = c.ink2)
        if (trailing != null) trailing() else if (onClick != null) OdIcon(OdIcon.ChevR, size = 18.dp, tint = c.ink2)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Od.c.line))

// ---------------------------------------------------------------- lights

@Composable
fun LightOrb(light: Color, glow: Color, size: Dp, modifier: Modifier = Modifier, stop: Float = 0.45f) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(
            Brush.radialGradient(0f to Color(0xFFFFFDF6), stop to light, 1f to glow, center = Offset(center.x, this.size.height * 0.40f), radius = r * 1.08f),
            radius = r,
        )
    }
}

@Composable
fun LightOrb(light: Light, size: Dp, modifier: Modifier = Modifier) = LightOrb(rgb(light.light), rgb(light.glow), size, modifier)

/**
 * 1-tap mood orb (README §5): 52dp sphere with a soft colored shadow; selected = 3dp bg gap + 2dp glow ring.
 * Press: 0.92 → 1.0 spring (damping 0.6).
 */
@Composable
fun MoodOrb(group: EmotionGroup, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, ringGap: Color = Od.c.surface) {
    val c = Od.c
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f), label = "orb")
    Column(
        modifier.width(56.dp).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val glow = group.glowColor
        Box(
            Modifier.scale(s).size(size)
                .dropShadow(CircleShape, Shadow(radius = 14.dp, color = glow.copy(alpha = if (c.dark) 0.35f else 0.45f), offset = DpOffset(0.dp, 4.dp)))
                .drawBehind {
                    if (selected) {
                        drawCircle(glow, this.size.minDimension / 2 + 5.dp.toPx())
                        drawCircle(ringGap, this.size.minDimension / 2 + 3.dp.toPx())
                    }
                },
        ) {
            LightOrb(group.lightColor, glow, size, stop = 0.38f)
        }
        Text(label, style = OdType.caption1.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
    }
}

@Composable
fun QuickMoodRow(selected: EmotionKey?, onPick: (EmotionKey) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        EmotionKey.QUICK.forEach { k -> MoodOrb(k.group!!, k.label, k == selected, { onPick(k) }) }
    }
}

/** One record on the "하루의 빛" strip. */
data class DayMark(val time: String, val emotions: List<EntryEmotion>)

/**
 * "하루의 빛" (README §5): 0–24 h axis; each record a glow dot with a 2.5dp surface ring, joined by a linear gradient that fades
 * out 9 % before the first and 7 % after the last dot.
 */
@Composable
fun DayLightStrip(marks: List<DayMark>, modifier: Modifier = Modifier, height: Dp = 14.dp, labels: Boolean = true, ticks: Boolean = !labels) {
    val c = Od.c
    val pts = marks.map { m ->
        val (h, mm) = m.time.split(":").map { it.trim().toIntOrNull() ?: 0 }
        ((h * 60 + mm) / 1440f).coerceIn(0f, 1f) to m
    }.sortedBy { it.first }
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(maxOf(height, 14.dp))) {
            val h = height.toPx()
            val top = (size.height - h) / 2
            drawRoundRect(c.surface2, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
            if (pts.isNotEmpty()) {
                val stops = buildList {
                    val first = pts.first(); val last = pts.last()
                    val fg = rgb(LightMixer.mix(first.second.emotions).glow)
                    val lg = rgb(LightMixer.mix(last.second.emotions).glow)
                    add((first.first - 0.09f).coerceAtLeast(0f) to fg.copy(alpha = 0f))
                    pts.forEach { (x, m) -> add(x to rgb(LightMixer.mix(m.emotions).glow)) }
                    add((last.first + 0.07f).coerceAtMost(1f) to lg.copy(alpha = 0f))
                }.distinctBy { it.first }.sortedBy { it.first }
                if (stops.size >= 2) drawRoundRect(
                    Brush.horizontalGradient(*stops.map { (x, col) -> x to col }.toTypedArray(), startX = 0f, endX = size.width),
                    Offset(0f, top), Size(size.width, h), CornerRadius(h / 2),
                )
                val r = 7.dp.toPx()
                pts.forEach { (x, m) ->
                    val glow = rgb(LightMixer.mix(m.emotions).glow)
                    val cx = (x * size.width).coerceIn(r, size.width - r)
                    val cy = size.height / 2
                    drawCircle(glow.copy(alpha = 0.5f), r + 1.dp.toPx(), Offset(cx, cy))
                    drawCircle(c.surface, r, Offset(cx, cy))
                    drawCircle(glow, r - 2.5.dp.toPx(), Offset(cx, cy))
                }
            }
        }
        if (labels && pts.isNotEmpty()) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp).height(34.dp)) {
                val w = maxWidth
                // Records close in time would print their labels on top of each other: the newest one keeps its label.
                var nextX = Float.MAX_VALUE
                val shown = pts.reversed().filter { (x, _) -> ((nextX - x) * w.value >= 60f).also { if (it) nextX = x } }
                shown.forEach { (x, m) ->
                    val names = m.emotions.sortedByDescending { it.intensity }.joinToString("·") { it.key.label }
                    Column(
                        Modifier.offset(x = ((w * x) - 50.dp).coerceIn(0.dp, maxOf(0.dp, w - 100.dp))).width(100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(names, style = OdType.caption2.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(m.time, style = OdType.caption2.copy(fontSize = 11.sp, lineHeight = 14.sp), color = c.ink2)
                    }
                }
            }
        }
        if (ticks) Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0시", "6시", "12시", "18시", "24시").forEach { Text(it, style = OdType.caption2.copy(fontSize = 10.5.sp), color = c.ink3) }
        }
    }
}

/** Empty "하루의 빛": a dotted line. */
@Composable
fun EmptyDayStrip(modifier: Modifier = Modifier) {
    val c = Od.c
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        var x = 6.5.dp.toPx()
        while (x < size.width) {
            drawCircle(c.ink3.copy(alpha = 0.45f), 1.6.dp.toPx(), Offset(x, size.height / 2)); x += 13.dp.toPx()
        }
    }
}

/** Day cell color (README §2): light = `light`, dark = `glow` mixed 12 % toward night. */
@Composable
fun cellColor(l: Light): Color = if (Od.c.dark) lerp(rgb(l.glow), OdColors.Night, 0.12f) else rgb(l.light)

// ---------------------------------------------------------------- Oni helpers

/**
 * Oni sized like the 시안's `oni(m, expr, size)`: an SVG box [size]² whose viewBox (-24 -20 560 470) puts the 512 lamp at
 * 0.914·size, 11.6 % from the top.
 */
@Composable
fun OniFigure(light: Light?, expression: OniExpression, size: Dp, modifier: Modifier = Modifier, animate: Boolean = true, halo: Boolean = true) {
    Box(modifier.size(size)) {
        Oni(light, expression, Modifier.align(Alignment.TopCenter).offset(y = size * 0.116f).size(size * 0.914f), animate, halo)
    }
}

/** Speech bubble with a small tail at the bottom center. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, style: TextStyle = OdType.say.copy(fontSize = 15.sp, lineHeight = 22.sp)) {
    val c = Od.c
    Box(modifier) {
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).size(12.dp).rotate(45f)
                .let { if (c.dark) it else it.dropShadow(RoundedCornerShape(2.dp), Shadow(radius = 6.dp, color = Color(0x0F2E2622), offset = DpOffset(0.dp, 3.dp))) }
                .clip(RoundedCornerShape(2.dp)).background(c.surface),
        )
        Box(Modifier.odCard(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text, style = style, textAlign = TextAlign.Center)
        }
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).size(12.dp).rotate(45f).clip(RoundedCornerShape(2.dp)).background(c.surface))
    }
}

// ---------------------------------------------------------------- photos

private val photoPalettes = listOf(
    listOf(0xFFF6D7B0, 0xFFE9A98A, 0xFF9C6E78, 0xFF4E4258), listOf(0xFFE6EEDC, 0xFFB9CFA7, 0xFF7C9B72, 0xFF4C5E47),
    listOf(0xFFDCE8F2, 0xFFA9C3DA, 0xFF6E8FAE, 0xFFE8DCC8), listOf(0xFFF3E4CF, 0xFFD9B892, 0xFF8E6A4F, 0xFF8E6A4F),
)

/** Film-tone placeholder used where no photo loads (README §7: 사진은 그라데이션 자리표시). */
@Composable
fun PhotoPlaceholder(seed: Int, modifier: Modifier = Modifier) {
    val p = photoPalettes[Math.floorMod(seed, photoPalettes.size)].map { Color(it) }
    Box(modifier.background(Brush.verticalGradient(p)).background(Brush.radialGradient(listOf(Color(0x59FFF0D2), Color.Transparent))))
}

// Ad slots live in core:billing-ads (AdBanner / AdNative), gated by the app's screen allowlist.

// ---------------------------------------------------------------- misc

/** Dashed rounded outline (AI-suggested tag chips, photo add tile). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))),
    )
}

/** "기쁨 4 · 설렘 2" */
fun List<EntryEmotion>.label(): String =
    if (isEmpty()) "" else joinToString(" · ") { if (it.key == EmotionKey.UNSURE) it.key.label else "${it.key.label} ${it.intensity}" }
