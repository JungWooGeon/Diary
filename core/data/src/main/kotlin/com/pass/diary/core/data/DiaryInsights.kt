package com.pass.diary.core.data

import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.SentenceMark

/**
 * On-device AI over saved entries (AI_GROWTH §2.1 E3–E5), bound in app/.../EmbeddingStore.kt. Off the main thread inside.
 * Empty = the model is missing, the switch is off, or nothing is close enough: the UI then hides the section quietly.
 */
interface DiaryInsights {
    /** E4 비슷했던 날: earlier entries close in feeling and situation. Never the same day, trash, or a crisis-worded entry. */
    suspend fun similarDays(entryId: String, max: Int = 3): List<Entry> = emptyList()

    /** E3: the body's most strongly felt sentences (cached per text). */
    suspend fun sentenceMarks(body: String): List<SentenceMark> = emptyList()

    /** E5 뜻으로 찾기: entry ids by meaning + shared words, best first. */
    suspend fun searchByMeaning(query: String, max: Int = 6): List<String> = emptyList()
}
