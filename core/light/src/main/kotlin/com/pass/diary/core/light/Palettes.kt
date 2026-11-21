package com.pass.diary.core.light

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.LightPaletteId

/**
 * 꾸미기 › 빛 팔레트, display only: stored/synced colors stay canonical. [remap] moves any color produced from the
 * canonical group colors (a group's light/glow, or a LightMixer mix of them) into the palette: pure group colors map
 * exactly, mixes follow by inverse-distance weighting of the anchors' Oklab shifts. "Off" grey never moves.
 */
class Palette private constructor(val id: LightPaletteId, glows: Map<EmotionGroup, Int>) {
    /** The palette's glow per group, in group order (swatches). */
    val glows: List<Int> = EmotionGroup.entries.map { glows.getValue(it) }

    private val anchors: List<Pair<DoubleArray, DoubleArray>> = buildList {
        for (g in EmotionGroup.entries) {
            val glow = glows.getValue(g)
            add(Oklab.fromRgb(g.glow) to Oklab.fromRgb(glow))
            add(Oklab.fromRgb(g.light) to Oklab.fromRgb(if (id == LightPaletteId.BASIC) g.light else pale(glow)))
        }
        for (off in listOf(EmotionGroup.OFF_LIGHT, EmotionGroup.OFF_GLOW)) Oklab.fromRgb(off).let { add(it to it) }
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun remap(rgb: Int): Int {
        if (id == LightPaletteId.BASIC) return rgb
        return cache.getOrPut(rgb and 0xFFFFFF) {
            val c = Oklab.fromRgb(rgb)
            var wSum = 0.0; val d = DoubleArray(3)
            for ((from, to) in anchors) {
                val dist2 = (0..2).sumOf { (c[it] - from[it]).let { x -> x * x } }
                if (dist2 < 1e-12) return@getOrPut Oklab.toRgb(to[0], to[1], to[2])
                val w = 1 / (dist2 * dist2) // sharp falloff: colors near one group follow that group
                wSum += w
                for (i in 0..2) d[i] += w * (to[i] - from[i])
            }
            Oklab.toRgb(c[0] + d[0] / wSum, c[1] + d[1] / wSum, c[2] + d[2] / wSum)
        }
    }

    companion object {
        /** The light (pale) variant of a palette glow: 62 % toward white, like the canonical light/glow pairs. */
        private fun pale(glow: Int): Int {
            fun ch(s: Int) = (glow shr s and 0xFF).let { it + ((255 - it) * 0.62).toInt() }
            return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        private fun of(id: LightPaletteId, bright: Int, calm: Int, flutter: Int, low: Int, worn: Int) = Palette(
            id, mapOf(EmotionGroup.BRIGHT to bright, EmotionGroup.CALM to calm, EmotionGroup.FLUTTER to flutter, EmotionGroup.LOW to low, EmotionGroup.WORN to worn),
        )

        val all: Map<LightPaletteId, Palette> = listOf(
            Palette(LightPaletteId.BASIC, EmotionGroup.entries.associateWith { it.glow }),
            of(LightPaletteId.SUNSET, 0xF0C36A, 0xF7A26B, 0xE58C8A, 0x7E86C4, 0xB98BB8),
            of(LightPaletteId.DAWN, 0xF3D29C, 0xA8D5C6, 0xF2B5C4, 0x8EA6D8, 0xB9A5DA),
            of(LightPaletteId.FIREFLY, 0xE6DD6E, 0x9BD47C, 0xF1A877, 0x6C9DC0, 0x9A8BC6),
            of(LightPaletteId.WINTER, 0xF6E9C9, 0xBFD7F2, 0xE7D9F7, 0x9FB6E6, 0xC9B8EC),
            of(LightPaletteId.SPRING, 0xFFE3A6, 0xC7E8B8, 0xF9C6D3, 0xB9D3F0, 0xD6C5F0),
        ).associateBy { it.id }

        operator fun get(id: LightPaletteId) = all.getValue(id)
    }
}
