package com.pass.diary.feature.calendar

import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayCoverTest {
    private fun entry(id: String, time: String, title: String = "", body: String = "", photo: Boolean = false) =
        Entry(id = id, date = "2026-10-12", time = time, createdAt = 0, updatedAt = 0, title = title, body = body,
            photos = if (photo) listOf(Photo(id = "p-$id", entryId = id, localPath = "/x/$id.jpg", w = 1, h = 1)) else emptyList())

    @Test fun photoBelongsToTheShownEntry() {
        val walk = entry("walk", "09:00", title = "한강 따라 걸은 날", photo = true)
        val lunch = entry("lunch", "13:00", body = "점심 먹고 한강 산책.")
        // The 편안함 record (body only) is shown when it is the day's only text; the walk photo must not ride along.
        val (shown, photo) = dayCover(listOf(entry("blank", "08:00", photo = true), lunch))
        assertEquals("lunch", shown.id)
        assertNull(photo)
        val (shown2, photo2) = dayCover(listOf(lunch, walk))
        assertEquals("walk", shown2.id)
        assertEquals("walk", photo2!!.entryId)
    }
}
