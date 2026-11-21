package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.ReplySource
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake LLM: answers from [script] in order (each answer streamed in 2 cumulative chunks); records requests. */
class FakeLlm(private vararg val script: String?) : LlmRuntime {
    val requests = mutableListOf<LlmRequest>()
    override fun generate(request: LlmRequest): Flow<String> = flow {
        val answer = script.getOrNull(requests.size).also { requests += request }
        if (answer == null) awaitCancellation() // simulates a hang -> timeout
        emit(answer.take(answer.length / 2)); emit(answer)
    }
    override fun close() = Unit
}

class RuntimeTest {
    private val prompts = LlmPrompts(TestAssets.json("ai/llm/prompts.json")!!.obj)
    private val filter = OutputFilter(TestAssets.json("ai/llm/filters.json")!!.obj)
    private val template = TemplateReplyEngine(Json.parseToJsonElement(TestAssets.resource("fixtures/replies.json")).jsonArray, random = Random(0))
    private fun engine(llm: FakeLlm) = LlmReplyEngine(llm, prompts, filter, template, timeoutMs = 500)
    private val tired = listOf(EntryEmotion(EmotionKey.TIRED, 3))
    private val good = "새벽까지 회계원리 정리하느라 고생 많았어. 시험 중에 눈이 감길 만큼 지쳤겠다."

    @Test fun llmReplyPassesFilter_singleEventNoStreaming() = runBlocking {
        val llm = FakeLlm(good)
        val events = engine(llm).reply("어제 새벽 4시까지 회계원리 정리", tired, emptyList()).toList()
        assertEquals(listOf(ReplyEvent.Done(good, ReplySource.LLM, null)), events)
        val req = llm.requests.single()
        assertEquals(prompts.reply.system, req.system)
        assertEquals(5, req.fewshot.size)
        assertEquals(0.5, req.sampling.temperature, 0.0)
    }

    @Test fun filterFailRetriesOnceThenTemplate() = runBlocking {
        // 1st: 존댓말, 2nd: lecture ending -> both rejected -> library reply
        val llm = FakeLlm("많이 힘드셨겠어요. 푹 쉬세요.", "피곤했겠다. 오늘은 일찍 자는 게 중요해.")
        val events = engine(llm).reply("야근함 회사에서", tired, listOf(Tag(TagKind.PLACE, "회사"))).toList()
        val done = events.single() as ReplyEvent.Done // the two rejected drafts never left the engine
        assertEquals(2, llm.requests.size)
        assertEquals(ReplySource.TEMPLATE, done.source)
        assertEquals("w2", done.templateId)
        assertEquals("회사에서 버틴 하루, 나 다 알아.", done.text)
    }

    @Test fun retrySucceeds() = runBlocking {
        val llm = FakeLlm("정말 辛苦했어. 고생했어.", good)
        val done = engine(llm).reply("새벽 4시까지 회계원리", tired, emptyList()).last() as ReplyEvent.Done
        assertEquals(ReplySource.LLM, done.source)
        assertEquals(good, done.text)
    }

    @Test fun inventedNameIsRejected() = runBlocking {
        val llm = FakeLlm("서연이랑 김치찌개 먹었구나. 맛있었겠다.", "서연이랑 김치찌개 먹었구나. 맛있었겠다.")
        val done = engine(llm).reply("동기 지민이랑 김치찌개 먹음", tired, emptyList()).last() as ReplyEvent.Done
        assertEquals(ReplySource.TEMPLATE, done.source)
    }

    @Test fun timeoutFallsBackToTemplate() = runBlocking {
        val done = engine(FakeLlm(null, null)).reply("피곤", tired, emptyList()).last() as ReplyEvent.Done
        assertEquals(ReplySource.TEMPLATE, done.source)
    }

    @Test fun sarcasmReadingAddsHint() = runBlocking {
        val reading = """{"bad":["혼자 PPT 만듦"],"praise":["최고의 팀플"],"sarcasm":true}"""
        val llm = FakeLlm(reading, "혼자 PPT까지 다 만들었는데 '최고의 팀플'이라니 속이 탔겠다. 오늘 정말 고생 많았어.")
        val diary = "와 정말 최고의 팀플이었다^^ 새벽 3시까지 PPT 혼자 만듦."
        val done = engine(llm).reply(diary, tired, emptyList()).last() as ReplyEvent.Done
        assertEquals(ReplySource.LLM, done.source)
        assertEquals(prompts.task("sarcasm").schema, llm.requests[0].jsonSchema) // constrained reading step
        assertTrue(llm.requests[1].user, llm.requests[1].user.startsWith(diary) && "비꼬는 말" in llm.requests[1].user && "'혼자 PPT 만듦'" in llm.requests[1].user)
    }

    @Test fun summaryReusesSarcasmReading() = runBlocking {
        val reading = """{"bad":["혼자 PPT 만듦"],"praise":["최고의 팀플"],"sarcasm":true}"""
        val llm = FakeLlm(reading, "혼자 PPT까지 다 만들었는데 '최고의 팀플'이라니 속이 탔겠다. 오늘 정말 고생 많았어.", "팀플 조원 대신 혼자 PPT 만들어서 짜증 남")
        val e = engine(llm)
        val diary = "와 정말 최고의 팀플이었다^^ 새벽 3시까지 PPT 혼자 만듦."
        e.reply(diary, tired, emptyList()).toList()
        assertEquals("팀플 조원 대신 혼자 PPT 만들어서 짜증 남", e.summary(diary))
        assertEquals(3, llm.requests.size) // reading not repeated
        assertTrue("비꼬는 말" in llm.requests[2].user)
    }

    @Test fun positiveUserMoodSkipsSarcasmReading() = runBlocking {
        val llm = FakeLlm("룸메가 끓여 둔 라면이 야근한 하루를 데워 줬구나. 그 마음이 참 고맙다.")
        engine(llm).reply("야근하고 왔는데 룸메가 라면 끓여놨다. 감동이다", tired, emptyList(), userMood = EmotionKey.GRATEFUL).toList()
        assertEquals(1, llm.requests.size)
    }

    @Test fun noReadingWithoutPraiseWords_userMoodHint() = runBlocking {
        val llm = FakeLlm(good)
        engine(llm).reply("새벽 4시까지 회계원리", tired, emptyList(), userMood = EmotionKey.TIRED).toList()
        assertEquals(1, llm.requests.size)
        assertTrue(llm.requests[0].user.endsWith("기분 — 피곤)"))
    }

    @Test fun emotionJsonMapsSynonymsAndDropsInventedTags() = runBlocking {
        val llm = FakeLlm("""{"bad":[],"praise":[],"sarcasm":false,"emotions":[{"name":"걱정","intensity":4},{"name":"행복","intensity":9},{"name":"없는감정","intensity":2}],"people":["엄마","서연"],"places":["학교"],"activities":[]}""")
        val (emos, tags) = engine(llm).emotions("엄마가 학교 앞에 왔다")!!
        assertEquals(listOf(EntryEmotion(EmotionKey.ANXIOUS, 4), EntryEmotion(EmotionKey.JOY, 5)), emos)
        assertEquals(listOf("엄마", "학교"), tags.map { it.value })
        assertTrue(llm.requests[0].jsonSchema!!.contains("\"enum\""))
    }

    @Test fun tidyFallsBackToTranscriptOnNumberHallucination() = runBlocking {
        val t = "몸무게는 사 점 이 키로였고"
        assertEquals("몸무게는 4.2kg이었다.", engine(FakeLlm("몸무게는 4.2kg이었다.")).tidy(t))
        assertEquals(t, engine(FakeLlm("몸무게는 5kg이었다.", "몸무게는 6kg이었다.")).tidy(t))
    }

    @Test fun pipelineCrisisShortCircuits() = runBlocking {
        val llm = FakeLlm(good)
        var llmAsked = false
        val p = pipeline { llmAsked = true; engine(llm) }
        val events = p.onSave("다 그만하고 싶다. 커터칼을 한참 쳐다봤다. 죽고 싶다").toList()
        assertEquals(1, events.size)
        assertTrue(events.single() is AiEvent.Crisis)
        assertTrue(!llmAsked && llm.requests.isEmpty())
    }

    @Test fun pipelineOrderUserEmotionWinsTemplateWithoutLlm() = runBlocking {
        val user = listOf(EntryEmotion(EmotionKey.ANNOYED, 4))
        val events = pipeline { null }.onSave("회사에서 야근했다", userEmotions = user).toList()
        val a = events[0] as AiEvent.Analysis
        assertEquals(user, a.emotions)
        assertTrue(a.suggestedTags.any { it.value == "회사" })
        val r = events[1] as AiEvent.Reply
        assertEquals(ReplySource.TEMPLATE, r.source)
        assertEquals(2, events.size)
    }

    @Test fun pipelineWithLlmUsesLlmEmotionsThenOneReply() = runBlocking {
        val emo = """{"bad":[],"praise":[],"sarcasm":false,"emotions":[{"name":"피곤","intensity":4}],"people":[],"places":["도서관"],"activities":[]}"""
        val e = engine(FakeLlm(emo, good))
        val events = pipeline { e }.onSave("새벽 4시까지 회계원리 정리하고 도서관 감").toList()
        val a = events[0] as AiEvent.Analysis
        assertEquals(SuggestionSource.LLM, a.source)
        assertEquals(listOf(EntryEmotion(EmotionKey.TIRED, 4)), a.suggestedEmotions)
        assertTrue(a.suggestedTags.any { it.value == "도서관" })
        assertEquals(listOf(AiEvent.Reply(good, ReplySource.LLM, null)), events.drop(1)) // no partial text, ever
    }

    @Test fun suggestFallsBackToClassifierWhenLlmJsonBroken() = runBlocking {
        val s = pipeline { engine(FakeLlm("not json")) }.suggest("그냥 피곤함")
        assertEquals(SuggestionSource.CLASSIFIER, s.source)
        assertEquals(EmotionKey.TIRED, s.emotions.first().key)
    }

    @Test fun chatWithoutLlmUsesLibraryAndJoinsAnswers() = runBlocking {
        val chat = OniChat({ null }, template, crisis)
        assertEquals(OniChat.FIRST, chat.next(emptyList())) // fixture has no questions
        assertEquals("카페 감\n좋았어", chat.compose(listOf("어디 갔어?" to "카페 감", "어땠어?" to " 좋았어")))
        val llmChat = OniChat({ engine(FakeLlm("카페에 갔구나. 누구랑 갔어?")) }, template, crisis)
        assertEquals("카페에 갔구나. 누구랑 갔어?", llmChat.next(listOf("오늘 어땠어?" to "카페 감")))
    }

    /** Review #2: a crisis answer in 대화로 쓰기 never reaches the LLM (neither the next question nor compose). */
    @Test fun chatCrisisAnswerStopsBeforeLlm() = runBlocking {
        val llm = FakeLlm("카페에 갔구나. 누구랑 갔어?", "정리한 일기")
        val chat = OniChat({ engine(llm) }, template, crisis)
        val turns = listOf("오늘 어땠어?" to "카페 감", "그다음엔?" to "다 그만하고 싶다. 죽고 싶다")
        assertTrue(chat.isCrisis(turns[1].second))
        assertEquals(null, chat.next(turns))
        assertEquals("카페 감\n다 그만하고 싶다. 죽고 싶다", chat.compose(turns))
        assertTrue(llm.requests.isEmpty())
    }

    private val crisis = CrisisDetector(TestAssets.json("ai/crisis.json")!!.obj)

    private fun pipeline(llm: () -> LlmReplyEngine?) = AiPipeline(
        CrisisDetector(TestAssets.json("ai/crisis.json")!!.obj),
        EmotionClassifier(null, null, EmotionKey.entries.map { it.key }, null),
        TagExtractor(TestAssets.json("ai/tags.json")!!.obj),
        template, llm,
    )

    // ---- ModelManager ----
    private class Device(override val totalRamBytes: Long, val free: Long = Long.MAX_VALUE, override val onUnmeteredNetwork: Boolean = true) : DeviceInfo {
        override fun freeBytes(dir: File) = free
    }

    private fun spec(url: String, data: ByteArray, sha: String? = sha(data)) =
        ModelSpec("e2b", "E2B", url, "m.litertlm", sha, data.size.toLong(), 8, false)
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun deviceGate() {
        val specs = ModelSpec.parse(TestAssets.json("ai/llm/models.json")!!.arr)
        val dir = Files.createTempDirectory("m").toFile()
        assertEquals(listOf("e2b"), ModelManager(specs, dir, Device(7_700_000_000)).supported().map { it.id }) // "8GB" phone
        assertEquals(listOf("e2b", "e4b"), ModelManager(specs, dir, Device(11_500_000_000)).supported().map { it.id })
        assertEquals(emptyList<String>(), ModelManager(specs, dir, Device(5_800_000_000)).supported().map { it.id }) // 6GB
        assertEquals(ModelManager.Gate.LOW_STORAGE, ModelManager(specs, dir, Device(8_000_000_000, free = 1_000_000_000)).gate(specs[0]))
        assertTrue(ModelManager(specs, dir, Device(8_000_000_000, onUnmeteredNetwork = false)).wifiRecommended)
    }

    @Test fun downloadResumesWithRangeAndVerifies() = runBlocking {
        val data = Random(3).nextBytes(300_000)
        val ranges = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/m") { ex ->
                val range = ex.requestHeaders.getFirst("Range").also { ranges += it }
                val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                ex.sendResponseHeaders(if (from > 0) 206 else 200, (data.size - from).toLong())
                ex.responseBody.use { it.write(data, from, data.size - from) }
            }
            start()
        }
        try {
            val dir = Files.createTempDirectory("m").toFile()
            val s = spec("http://127.0.0.1:${server.address.port}/m", data)
            File(dir, "m.litertlm.part").writeBytes(data.copyOf(100_000)) // interrupted earlier
            val mm = ModelManager(listOf(s), dir, Device(8_000_000_000))
            val states = mm.download(s).toList()
            assertEquals("bytes=100000-", ranges.single())
            assertTrue(states.last() is DownloadState.Done)
            assertTrue(states.any { it is DownloadState.Verifying })
            assertTrue(data.contentEquals(mm.file(s).readBytes()) && mm.isInstalled(s) && mm.installed() == s)
            mm.delete(s)
            assertTrue(!mm.isInstalled(s))

            val bad = spec(s.url, data, sha = "0".repeat(64))
            val failed = ModelManager(listOf(bad), dir, Device(8_000_000_000)).download(bad).last()
            assertEquals(DownloadState.Failed("CHECKSUM"), failed)
            assertTrue(!File(dir, "m.litertlm.part").exists())
        } finally { server.stop(0) }
    }

    /** Review #10: 취소 while the finished file is being verified must not end with the model installed. */
    @Test fun cancelDuringVerifyWinsOverRename() = runBlocking {
        val data = Random(4).nextBytes(1000)
        val dir = Files.createTempDirectory("m").toFile()
        val s = spec("http://127.0.0.1:1/none", data)
        File(dir, "m.litertlm.part").writeBytes(data) // all bytes here, about to verify
        val mm = ModelManager(listOf(s), dir, Device(8_000_000_000))
        mm.verify = { f -> sha(f.readBytes()).also { mm.delete(s) } } // the user taps 취소 during sha256
        assertEquals(DownloadState.Failed("CANCELLED"), mm.download(s).last())
        assertTrue(!mm.isInstalled(s) && !mm.file(s).exists() && !File(dir, "m.litertlm.part").exists())

        // a later, uncancelled download installs normally
        mm.verify = { f -> sha(f.readBytes()) }
        File(dir, "m.litertlm.part").writeBytes(data)
        assertTrue(mm.download(s).last() is DownloadState.Done)
        assertTrue(mm.isInstalled(s))
    }

    @Test fun downloadRefusedOnLowRam() = runBlocking {
        val s = spec("http://127.0.0.1:1/none", ByteArray(10))
        assertEquals(DownloadState.Failed("LOW_RAM"), ModelManager(listOf(s), Files.createTempDirectory("m").toFile(), Device(4_000_000_000)).download(s).last())
    }
}
