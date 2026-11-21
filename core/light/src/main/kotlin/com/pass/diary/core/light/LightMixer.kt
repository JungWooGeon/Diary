package com.pass.diary.core.light

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Oni's light. [light] = calendar cell / dome inside, [glow] = halo / chart accent (0xRRGGBB). [strength] 0..1. */
data class Light(val light: Int, val glow: Int, val strength: Float, val off: Boolean) {
    val lightHex get() = hex(light)
    val glowHex get() = hex(glow)

    companion object {
        fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)
        fun parse(hex: String): Int = hex.removePrefix("#").toInt(16)
    }
}

/**
 * Platform-neutral spec (port 1:1 to TypeScript; shared/light-test-vectors.json pins the numbers):
 * 1. Drop `unsure`. w = intensity (1..5). T = Σw. T == 0 → [OFF].
 * 2. For light and glow separately: convert each emotion's group color to OKLab; L = Σw·L / T;
 *    hue = atan2(Σw·b, Σw·a); chroma C = Σw·C / T (averaging chroma, not a/b, keeps mixes from going muddy;
 *    if the a/b vectors cancel out (|Σw·(a,b)| < 1e-9) C = 0).
 * 3. strength s = min(1, T / 5). Chroma ×(0.6 + 0.4s), L ×(0.92 + 0.08s): stronger feelings, brighter, purer light.
 * 4. Back to sRGB, clamp each channel to 0..1, round(×255).
 */
object LightMixer {
    val OFF = Light(EmotionGroup.OFF_LIGHT, EmotionGroup.OFF_GLOW, 0f, off = true)
    const val FULL_STRENGTH_TOTAL = 5.0

    fun mix(emotions: List<EntryEmotion>): Light {
        val lit = emotions.filter { it.key != EmotionKey.UNSURE && it.key.group != null }
        val total = lit.sumOf { it.intensity }.toDouble()
        if (total == 0.0) return OFF
        val s = min(1.0, total / FULL_STRENGTH_TOTAL)
        fun blend(color: (EmotionGroup) -> Int): Int {
            var l = 0.0; var a = 0.0; var b = 0.0; var c = 0.0
            for (e in lit) {
                val lab = Oklab.fromRgb(color(e.key.group!!))
                val w = e.intensity
                l += w * lab[0]; a += w * lab[1]; b += w * lab[2]; c += w * hypot(lab[1], lab[2])
            }
            val hue = atan2(b, a)
            val chroma = if (hypot(a, b) < 1e-9) 0.0 else c / total * (0.6 + 0.4 * s)
            val lightness = l / total * (0.92 + 0.08 * s)
            return Oklab.toRgb(lightness, chroma * cos(hue), chroma * sin(hue))
        }
        return Light(blend { it.light }, blend { it.glow }, s.toFloat(), off = false)
    }

    /** The group with the largest total intensity (ties: group order); null when nothing is lit. Picks Oni's face. */
    fun dominantGroup(emotions: List<EntryEmotion>): EmotionGroup? = emotions
        .filter { it.key.group != null }
        .groupBy { it.key.group!! }
        .maxWithOrNull(compareBy<Map.Entry<EmotionGroup, List<EntryEmotion>>> { g -> g.value.sumOf { it.intensity } }.thenByDescending { it.key.ordinal })
        ?.key

    /** "하루의 빛": all of a day's emotions mixed into one light (Oni on the today tab). */
    fun mixDay(entries: List<List<EntryEmotion>>): Light = mix(entries.flatten())

    /**
     * Gradient stops for a day with several entries: each entry's own light at its time of day (0 = 00:00, 1 = 24:00),
     * sorted by time. Calendar cells just use the colors in order; the "하루의 빛" band uses the positions.
     */
    fun dayGradient(entries: List<Pair<String, List<EntryEmotion>>>): List<Pair<Float, Light>> =
        entries.map { (time, emotions) -> minuteOfDay(time) / 1440f to mix(emotions) }.sortedBy { it.first }

    private fun minuteOfDay(time: String): Int {
        val (h, m) = time.split(":").map { it.trim().toInt() }
        return (h * 60 + m).coerceIn(0, 1439)
    }
}

/** Björn Ottosson's OKLab (https://bottosson.github.io/posts/oklab/). */
object Oklab {
    fun fromRgb(rgb: Int): DoubleArray {
        val r = toLinear((rgb shr 16 and 0xFF) / 255.0)
        val g = toLinear((rgb shr 8 and 0xFF) / 255.0)
        val b = toLinear((rgb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun toRgb(lL: Double, a: Double, b: Double): Int {
        val l = (lL + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lL - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lL - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return (channel(r) shl 16) or (channel(g) shl 8) or channel(bl)
    }

    private fun channel(linear: Double) = (fromLinear(linear.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun fromLinear(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055
}
