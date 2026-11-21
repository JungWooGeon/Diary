package com.pass.diary.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import com.pass.diary.core.light.Light
import com.pass.diary.core.light.LightMixer
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EntryEmotion
import kotlin.math.roundToInt

/**
 * Oni's faces from 일기장_캐릭터_시안 (core/designsystem/tools/oni_layers.py). Each face is three layers so the lamp can take
 * any mixed light at runtime: [base] + light·[l] + glow·[g] (the drawing is linear in the two colors). The halo is drawn
 * live in [Oni] so it can breathe.
 */
enum class OniExpression(@DrawableRes val base: Int, @DrawableRes val l: Int, @DrawableRes val g: Int) {
    DEFAULT(R.drawable.oni_default, R.drawable.oni_default_l, R.drawable.oni_default_g),
    HAPPY(R.drawable.oni_happy, R.drawable.oni_happy_l, R.drawable.oni_happy_g),
    CALM(R.drawable.oni_calm, R.drawable.oni_calm_l, R.drawable.oni_calm_g),
    FLUTTER(R.drawable.oni_flutter, R.drawable.oni_flutter_l, R.drawable.oni_flutter_g),
    SAD(R.drawable.oni_sad, R.drawable.oni_sad_l, R.drawable.oni_sad_g),
    TIRED(R.drawable.oni_tired, R.drawable.oni_tired_l, R.drawable.oni_tired_g),
    SLEEP(R.drawable.oni_sleep, R.drawable.oni_sleep_l, R.drawable.oni_sleep_g),
    THINK(R.drawable.oni_think, R.drawable.oni_think_l, R.drawable.oni_think_g),
    COMFORT(R.drawable.oni_comfort, R.drawable.oni_comfort_l, R.drawable.oni_comfort_g),
    SURPRISED(R.drawable.oni_surprised, R.drawable.oni_surprised_l, R.drawable.oni_surprised_g),
    CHEER(R.drawable.oni_cheer, R.drawable.oni_cheer_l, R.drawable.oni_cheer_g),
    LETTER(R.drawable.oni_letter, R.drawable.oni_letter_l, R.drawable.oni_letter_g);

    companion object {
        /** Face follows the light (캐릭터 README §7: never a big smile on a blue light). null = nothing lit → sleepy. */
        fun forGroup(group: EmotionGroup?): OniExpression = when (group) {
            EmotionGroup.BRIGHT -> HAPPY
            EmotionGroup.CALM -> CALM
            EmotionGroup.FLUTTER -> FLUTTER
            EmotionGroup.LOW -> SAD
            EmotionGroup.WORN -> TIRED
            null -> SLEEP
        }

        /**
         * State rules: no entry → 졸음; `unsure` only → 기본(꺼진 빛); low/worn → 토닥(comfort) right after saving, otherwise
         * the group face; reply being written → 편지.
         */
        fun forState(emotions: List<EntryEmotion>, justSaved: Boolean = false, writingReply: Boolean = false): OniExpression {
            if (writingReply) return LETTER
            if (emotions.isEmpty()) return SLEEP
            val g = LightMixer.dominantGroup(emotions) ?: return DEFAULT
            if (justSaved) return when (g) {
                EmotionGroup.BRIGHT -> CHEER
                EmotionGroup.LOW, EmotionGroup.WORN -> COMFORT
                else -> forGroup(g)
            }
            return forGroup(g)
        }
    }
}

/**
 * The mood lamp. Breathing light per 캐릭터 README §9: 4 s cycle, halo 86→106 % and 45→100 % opacity; floats up 3.2 % on a
 * 2 s cycle. Light changes cross-fade in 0.56 s. [light] null = brand warm light (no mood chosen yet).
 */
@Composable
fun Oni(light: Light?, expression: OniExpression, modifier: Modifier = Modifier, animate: Boolean = true, halo: Boolean = true) {
    val sleepingOff = light?.off == true && expression == OniExpression.SLEEP
    val lightC by animateColorAsState(light?.let { rgb(it.light) } ?: OdColors.BrandLight, tween(560, easing = FastOutSlowInEasing), label = "light")
    val glow by animateColorAsState(light?.let { rgb(it.glow) } ?: OdColors.BrandGlow, tween(560, easing = FastOutSlowInEasing), label = "glow")
    val power by animateFloatAsState(
        when { sleepingOff || !halo -> 0f; light == null -> 0.85f; light.off -> 0.3f; else -> 0.6f + 0.4f * light.strength }, tween(560), label = "power",
    )
    val t = rememberInfiniteTransition(label = "oni")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val b = if (animate) breath else 1f
    val f = if (animate) float else 0f

    Box(
        modifier
            .aspectRatio(1f)
            .drawBehind {
                if (power <= 0f) return@drawBehind
                val scale = 0.86f + 0.20f * b
                val alpha = (0.45f + 0.55f * b) * power
                val center = Offset(size.width / 2, size.height * (232f / 512f))
                val radius = size.width * (250f / 512f) * scale
                drawCircle(
                    Brush.radialGradient(
                        0f to glow.copy(alpha = 0.9f * alpha), 0.45f to glow.copy(alpha = 0.5f * alpha),
                        0.72f to glow.copy(alpha = 0.16f * alpha), 1f to Color.Transparent,
                        center = center, radius = radius,
                    ),
                    radius = radius, center = center,
                )
            },
    ) {
        Crossfade(if (sleepingOff) null else expression, animationSpec = tween(560), label = "face") { e ->
            val layer = Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                translationY = -size.height * 0.032f * f
                if (animate) {
                    scaleX = 1f + 0.012f * (1f - f)
                    scaleY = 1f - 0.014f * (1f - f)
                }
                transformOrigin = TransformOrigin(0.5f, 0.85f)
            }
            if (e == null) {
                val off = ImageBitmap.imageResource(R.drawable.oni_off)
                Canvas(layer) { drawImage(off, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High) }
            } else {
                val base = ImageBitmap.imageResource(e.base)
                val l = ImageBitmap.imageResource(e.l)
                val g = ImageBitmap.imageResource(e.g)
                Canvas(layer) {
                    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    drawImage(base, dstSize = dst, filterQuality = FilterQuality.High)
                    drawImage(l, dstSize = dst, colorFilter = ColorFilter.tint(lightC, BlendMode.SrcIn), blendMode = BlendMode.Plus, filterQuality = FilterQuality.High)
                    drawImage(g, dstSize = dst, colorFilter = ColorFilter.tint(glow, BlendMode.SrcIn), blendMode = BlendMode.Plus, filterQuality = FilterQuality.High)
                }
            }
        }
    }
}
