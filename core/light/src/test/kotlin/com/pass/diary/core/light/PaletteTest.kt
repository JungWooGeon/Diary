package com.pass.diary.core.light

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.LightPaletteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PaletteTest {
    @Test fun basicIsIdentity() {
        assertEquals(0x123456, Palette[LightPaletteId.BASIC].remap(0x123456))
    }

    @Test fun groupGlowsMapExactlyAndOffStays() {
        val p = Palette[LightPaletteId.SUNSET]
        EmotionGroup.entries.forEachIndexed { i, g -> assertEquals(p.glows[i], p.remap(g.glow)) }
        assertEquals(EmotionGroup.OFF_GLOW, p.remap(EmotionGroup.OFF_GLOW))
    }

    @Test fun mixesMove() {
        val mix = LightMixer.mix(listOf(EntryEmotion(EmotionKey.JOY, 3), EntryEmotion(EmotionKey.SAD, 2))).glow
        assertNotEquals(mix, Palette[LightPaletteId.WINTER].remap(mix))
    }
}
