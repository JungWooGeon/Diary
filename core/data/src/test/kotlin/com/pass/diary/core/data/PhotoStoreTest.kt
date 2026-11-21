package com.pass.diary.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStoreTest {
    @Test fun `long side is capped at 1600 keeping aspect`() {
        assertEquals(1600 to 1200, PhotoStore.scaledSize(4000, 3000))
        assertEquals(900 to 1600, PhotoStore.scaledSize(2700, 4800))
        assertEquals(800 to 600, PhotoStore.scaledSize(800, 600))
    }

    @Test fun `sample size never decodes below 1600`() {
        assertEquals(2, PhotoStore.sampleSize(4000, 3000)) // 2000 >= 1600, 1000 < 1600
        assertEquals(1, PhotoStore.sampleSize(3000, 2000))
        assertEquals(4, PhotoStore.sampleSize(8000, 6000))
        assertEquals(1, PhotoStore.sampleSize(100, 100))
    }
}
