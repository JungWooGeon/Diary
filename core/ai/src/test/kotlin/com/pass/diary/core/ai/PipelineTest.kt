package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.ReplySource
import kotlin.random.Random
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTest {
    private val template = TemplateReplyEngine(Json.parseToJsonElement(TestAssets.resource("fixtures/replies.json")).jsonArray, random = Random(0))
    private val crisis = CrisisDetector(TestAssets.json("ai/crisis.json")!!.obj)
    private val pipeline = AiPipeline(
        crisis,
        EmotionClassifier(null, null, EmotionKey.entries.map { it.key }, null),
        TagExtractor(TestAssets.json("ai/tags.json")!!.obj),
        template,
    )

    @Test fun pipelineCrisisShortCircuits() = runBlocking {
        val events = pipeline.onSave("다 그만하고 싶다. 커터칼을 한참 쳐다봤다. 죽고 싶다").toList()
        assertEquals(1, events.size)
        assertTrue(events.single() is AiEvent.Crisis)
    }

    @Test fun pipelineOrderUserEmotionWinsThenTemplateReply() = runBlocking {
        val user = listOf(EntryEmotion(EmotionKey.ANNOYED, 4))
        val events = pipeline.onSave("회사에서 야근했다", userEmotions = user).toList()
        val a = events[0] as AiEvent.Analysis
        assertEquals(user, a.emotions)
        assertTrue(a.suggestedTags.any { it.value == "회사" })
        val r = events[1] as AiEvent.Reply
        assertEquals(ReplySource.TEMPLATE, r.source)
        assertEquals(2, events.size)
    }

    @Test fun suggestUsesClassifier() = runBlocking {
        val s = pipeline.suggest("그냥 피곤함")
        assertEquals(SuggestionSource.CLASSIFIER, s.source)
        assertEquals(EmotionKey.TIRED, s.emotions.first().key)
    }

    @Test fun chatUsesLibraryAndJoinsAnswers() = runBlocking {
        val chat = OniChat(template, crisis)
        assertEquals(OniChat.FIRST, chat.next(emptyList())) // fixture has no questions
        assertEquals("카페 감\n좋았어", chat.compose(listOf("어디 갔어?" to "카페 감", "어땠어?" to " 좋았어")))
    }

    @Test fun chatCrisisAnswerStops() = runBlocking {
        val chat = OniChat(template, crisis)
        val turns = listOf("오늘 어땠어?" to "카페 감", "그다음엔?" to "다 그만하고 싶다. 죽고 싶다")
        assertTrue(chat.isCrisis(turns[1].second))
        assertEquals(null, chat.next(turns))
    }
}
