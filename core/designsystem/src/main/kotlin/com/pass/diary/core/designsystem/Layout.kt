package com.pass.diary.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * How the window is laid out, decided once at the app root from the M3 window size class.
 * Compact (phones, portrait) = the 시안 as drawn; every field false.
 */
@Immutable
data class WindowLayout(
    /** ≥ 600dp wide (tablets, unfolded foldables, landscape phones): navigation rail instead of the bottom bar. */
    val rail: Boolean,
    /** ≥ 840dp wide: list–detail screens show both panes side by side. */
    val twoPane: Boolean,
    /** A real large screen (≥ 600dp wide and ≥ 480dp tall; not a landscape phone): type one step bigger. */
    val large: Boolean,
    /** < 480dp tall (landscape phones): heroes shrink so the content still shows. */
    val short: Boolean = false,
) {
    /** What the bottom bar covers at the bottom of tab screens (nothing when the rail is used). */
    val bottomBar: Dp get() = if (rail) 0.dp else 68.dp

    companion object {
        fun of(widthDp: Int, heightDp: Int) = WindowLayout(
            rail = widthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            twoPane = widthDp >= WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
            large = widthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND && heightDp >= WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
            short = heightDp < WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
        )
        val Compact = WindowLayout(rail = false, twoPane = false, large = false)

        /** Tablets read a touch bigger (README §3 sizes are for a 360dp phone held close). */
        const val LARGE_FONT_SCALE = 1.08f
    }
}

/** The current window's [WindowLayout] (M3 adaptive). Call once at the root and provide [LocalWindowLayout]. */
@Composable
fun currentWindowLayout(): WindowLayout = currentWindowAdaptiveInfo().windowSizeClass.let { WindowLayout.of(it.minWidthDp, it.minHeightDp) }

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.Compact }

val Od.layout: WindowLayout @Composable @ReadOnlyComposable get() = LocalWindowLayout.current

/** Longest comfortable line on a big screen; text columns stop growing here. */
val ReadableWidth = 680.dp

/** Full width, centered, capped at [max]: content columns on tablets don't stretch lines across the screen. A no-op on phones. */
fun Modifier.readableWidth(max: Dp = ReadableWidth) =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** 1dp hairline between two panes. */
@Composable
fun PaneDivider(modifier: Modifier = Modifier) = Box(modifier.fillMaxHeight().width(1.dp).background(Od.c.line))

/** The settings row shown in the detail pane (마이 on tablets): that [ListRow] is highlighted. */
val LocalSelectedRow = compositionLocalOf<String?> { null }
