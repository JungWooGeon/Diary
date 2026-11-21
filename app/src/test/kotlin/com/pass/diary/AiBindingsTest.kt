package com.pass.diary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pass.diary.core.ai.CrisisDetector
import com.pass.diary.core.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.pass.diary.core.model.Entry
import com.pass.diary.feature.write.ChatTurn
import com.pass.diary.feature.write.ReplyResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiBindingsTest {
    private fun provider(aiReply: Boolean): AiReplyProvider {
        val file = File.createTempFile("settings", ".preferences_pb").apply { delete(); deleteOnExit() }
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file }) { 0 }
        runBlocking { repo.setAiReply(aiReply) }
        val crisisJson = Json.parseToJsonElement(File(repoRoot, "shared/ai/crisis.json").readText()).jsonObject
        fun <T> never() = dagger.Lazy<T> { error("AI off: must not load") }
        return AiReplyProvider({ CrisisDetector(crisisJson) }, never(), never(), never(), repo)
    }
    private val repoRoot = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "shared/ai").isDirectory }
    private val entry = Entry(id = "x", date = "2026-11-21", time = "10:00", createdAt = 0, updatedAt = 0, body = "오늘 민지랑 카페 갔다")

    @Test fun replyOff_noClassifier_butCrisisStillShown() = runBlocking {
        val p = provider(aiReply = false)
        assertEquals(listOf(ReplyResult.Off), p.reply(entry, emptySet()).toList())
        assertEquals(listOf(ReplyResult.Crisis), p.reply(entry.copy(body = "죽고 싶다"), emptySet()).toList())
    }

    @Test fun chatTurnsPairQuestionsWithAnswers_skippedDropped() {
        val h = listOf(ChatTurn(true, "Q1"), ChatTurn(false, "A1"), ChatTurn(true, "Q2(넘김)"), ChatTurn(true, "Q3"), ChatTurn(false, "A3"))
        assertEquals(listOf("Q1" to "A1", "Q3" to "A3"), h.toPairs())
    }

    @Test fun aiTextSkipsBlankTitle() {
        val e = Entry(id = "x", date = "2026-11-21", time = "10:00", createdAt = 0, updatedAt = 0, body = "본문")
        assertEquals("본문", e.aiText())
        assertEquals("제목\n본문", e.copy(title = "제목").aiText())
    }
}
