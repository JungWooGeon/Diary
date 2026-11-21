package com.pass.diary.core.vision

import com.pass.diary.core.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTagsTest {
    @Test fun confidentKnownLabelsBecomeKoreanTags() {
        val tags = PhotoTags.from(
            listOf(
                ImageLabel("Fun", 0.95f), // not a diary word
                ImageLabel("Dog", 0.9f), ImageLabel("Beach", 0.8f), ImageLabel("Sea", 0.78f), // 바다 once
                ImageLabel("Cake", 0.5f), // too unsure
                ImageLabel("Flower", 0.75f), ImageLabel("Sky", 0.74f), // over max 3
            ),
        )
        assertEquals(listOf("강아지", "바다", "꽃"), tags.map { it.value })
        assertEquals(listOf(TagKind.PERSON, TagKind.PLACE, TagKind.FREE), tags.map { it.kind })
        assertEquals(true, tags.all { it.auto })
    }
}
