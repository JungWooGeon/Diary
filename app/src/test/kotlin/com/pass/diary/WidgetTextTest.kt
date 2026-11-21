package com.pass.diary

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetTextTest {
    private val sad = Entry(id = "a", date = "2026-11-21", time = "21:00", createdAt = 0, updatedAt = 0, body = "x", emotions = listOf(EntryEmotion(EmotionKey.SAD, 4)))

    @Test fun hiddenShowsOnlyCount() {
        val (title, sub) = oniWidgetText(listOf(sad, sad.copy(id = "b")), hidden = true)
        assertEquals("오늘 기록 2개", title)
        assertFalse((title + sub).contains(EmotionKey.SAD.label))
    }

    @Test fun shownNamesEmotion() {
        assertEquals(EmotionKey.SAD.label, oniWidgetText(listOf(sad), hidden = false).first)
    }
}
