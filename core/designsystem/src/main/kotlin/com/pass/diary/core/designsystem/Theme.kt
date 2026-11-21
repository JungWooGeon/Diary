package com.pass.diary.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pass.diary.core.light.Palette
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.FontId
import com.pass.diary.core.model.LightPaletteId

/** Screen 시안 README §2 tokens. UI is paper/night + ink; the only saturated colors are emotion lights. */
@Immutable
data class OdPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val primary: Color,
    val onPrimary: Color,
    val ok: Color,
    val ad: Color,
    /** Bottom bar: opaque bg (at 94 % scrolled text showed through the tab labels). */
    val navBg: Color,
    val dark: Boolean,
)

val LightPalette = OdPalette(
    bg = Color(0xFFFBF7F1), surface = Color.White, surface2 = Color(0xFFF4EEE6), line = Color(0xFFECE4DA),
    ink = Color(0xFF2E2622), ink2 = Color(0xFF6B5E55), ink3 = Color(0xFF75685F),
    primary = Color(0xFF2E2622), onPrimary = Color(0xFFFBF7F1), ok = Color(0xFF3F8A5A), ad = Color(0xFFF1ECE5),
    navBg = Color(0xFFFBF7F1), dark = false,
)

/** 꾸미기 › 종이: warmer, slightly darker paper; same ink logic as light. */
val PaperPalette = OdPalette(
    bg = Color(0xFFF3EBDD), surface = Color(0xFFFBF6EC), surface2 = Color(0xFFEBE1CF), line = Color(0xFFE2D5C0),
    ink = Color(0xFF3A2E25), ink2 = Color(0xFF6E5D4E), ink3 = Color(0xFF7A6857),
    primary = Color(0xFF3A2E25), onPrimary = Color(0xFFF3EBDD), ok = Color(0xFF3F8A5A), ad = Color(0xFFEAE0CE),
    navBg = Color(0xFFF3EBDD), dark = false,
)

val DarkPalette = OdPalette(
    bg = Color(0xFF1C1830), surface = Color(0xFF262141), surface2 = Color(0xFF302A4F), line = Color(0xFF3A3358),
    ink = Color(0xFFF4EFE8), ink2 = Color(0xFFBDB5CC), ink3 = Color(0xFF9A92AF),
    primary = Color(0xFFFFF0CC), onPrimary = Color(0xFF2E2622), ok = Color(0xFF8FD3A4), ad = Color(0xFF2A2545),
    navBg = Color(0xFF1C1830), dark = true,
)

/** Brand / character tokens (온이 README §5). */
object OdColors {
    val Paper = Color(0xFFFBF7F1)
    val Ink = Color(0xFF2E2622)
    val Night = Color(0xFF1C1830)
    val BrandLight = Color(0xFFFFF0CC)
    val BrandGlow = Color(0xFFF5A957)
    val OffLight = rgb(EmotionGroup.OFF_LIGHT)
    val OffGlow = rgb(EmotionGroup.OFF_GLOW)
    /** Sleepy / no-light ambient (오늘 기록 없음, 잠금). */
    val Dusk = Color(0xFF9A90B8)
    val PremiumFrom = Color(0xFFF7D98B)
    val PremiumTo = Color(0xFFF2B66C)
    val PremiumInk = Color(0xFF4A2F12)
    /** Calendar-day numbers on lit cells: always dark (light = ink, dark = night). */
    val OnLight = Color(0xFF2E2622)
}

/**
 * Active 꾸미기 choices. Snapshot state, so every composable/draw that reads a color or text style through here
 * updates when they change. Set by the app from Settings (after premium gating).
 */
object OdLook {
    var palette by mutableStateOf(Palette[LightPaletteId.BASIC])
    var font by mutableStateOf(FontId.GOTHIC)
}

/** An emotion-light color (0xRRGGBB, canonical) as shown in the active palette. */
fun rgb(rgb: Int) = Color(0xFF000000 or (OdLook.palette.remap(rgb).toLong() and 0xFFFFFF))
val EmotionGroup.lightColor get() = rgb(light)
val EmotionGroup.glowColor get() = rgb(glow)

/** 부드러운 명조: the system serif (Noto Serif CJK on most phones). ponytail: bundle a subset serif if a phone lacks Korean serif. */
private val SerifFamily = FontFamily.Serif

/** 손글씨 (premium): Gaegu (SIL OFL 1.1), subset like Pretendard. */
val HandFamily = FontFamily(Font(R.font.gaegu, FontWeight.Normal)) // bold is synthesized: a second weight would add 1.5 MB

fun FontId.family(): FontFamily = when (this) { FontId.GOTHIC -> Pretendard; FontId.SERIF -> SerifFamily; FontId.HAND -> HandFamily }

/** Pretendard (SIL OFL 1.1), variable, subset to KS X 1001 Hangul + Latin. License: app assets/licenses/. */
@OptIn(ExperimentalTextApi::class)
val Pretendard = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.pretendard, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

// Gaegu draws small for its size; nudge it up so 손글씨 reads like the others.
private fun style(size: Double, line: Double, weight: Int, tracking: Double = -0.2) = TextStyle(
    fontFamily = OdLook.font.family(), fontSize = (size * if (OdLook.font == FontId.HAND) 1.15 else 1.0).sp, lineHeight = line.sp, fontWeight = FontWeight(weight), letterSpacing = tracking.sp,
    // keep-all: break between words, never inside a Korean word.
    lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase), hyphens = Hyphens.None,
    localeList = LocaleList("ko-KR"),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** README §3 type scale. Getters, so the 꾸미기 font applies everywhere (OdLook.font is snapshot state). */
object OdType {
    val display get() = style(26.0, 36.0, 800, -0.6)
    val title1 get() = style(22.0, 31.0, 700, -0.5)
    val title2 get() = style(18.0, 26.0, 700, -0.4)
    val title3 get() = style(15.0, 22.0, 700, -0.3)
    val body1 get() = style(15.0, 24.0, 400)
    val diary get() = style(15.0, 27.0, 400)
    val body2 get() = style(14.0, 21.0, 400)
    val say get() = style(16.0, 25.0, 500)
    val reply get() = style(16.5, 28.0, 500)
    val caption1 get() = style(13.0, 19.0, 400)
    val caption2 get() = style(12.0, 17.0, 400)
    val tab get() = style(11.0, 14.0, 600, 0.0)
    val button get() = style(16.0, 22.0, 700)
    val buttonSm get() = style(14.0, 20.0, 700)
    val chip get() = style(14.0, 20.0, 600)
}

val LocalOd = staticCompositionLocalOf { LightPalette }

/** Ads disappear entirely for premium users (README §1.4). Provided by the app. */
val LocalPremium = staticCompositionLocalOf { false }

object Od {
    val c: OdPalette @Composable @ReadOnlyComposable get() = LocalOd.current
    val premium: Boolean @Composable @ReadOnlyComposable get() = LocalPremium.current
}

private fun scheme(p: OdPalette) = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.surface2, onPrimaryContainer = p.ink,
    secondary = p.ink2, onSecondary = p.bg, secondaryContainer = p.surface2, onSecondaryContainer = p.ink,
    background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.ink2,
    surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.surface2, outline = p.ink3, outlineVariant = p.line, error = if (p.dark) Color(0xFFF08A80) else Color(0xFFC2453A),
)

private fun type() = Typography(
    headlineMedium = OdType.display, headlineSmall = OdType.title1, titleLarge = OdType.title1, titleMedium = OdType.title2,
    titleSmall = OdType.title3, bodyLarge = OdType.body1, bodyMedium = OdType.body2, bodySmall = OdType.caption1,
    labelLarge = OdType.chip, labelMedium = OdType.caption2, labelSmall = OdType.tab,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun OdTheme(darkTheme: Boolean, premium: Boolean = false, paper: Boolean = false, content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else if (paper) PaperPalette else LightPalette
    MaterialTheme(colorScheme = scheme(p), typography = type(), shapes = AppShapes) {
        CompositionLocalProvider(LocalOd provides p, LocalPremium provides premium, LocalContentColor provides p.ink, content = content)
    }
}
