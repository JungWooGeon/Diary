package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule components against the shared/ specs and their own test cases. */
class RulesTest {
    @Test fun cleanDropsEchoedSpeakerLabel() {
        assertEquals("영화 보러 가기로 했구나!", LlmReplyEngine.clean("온이: 영화 보러 가기로 했구나!<end_of_turn>"))
        assertEquals("영화 보러 가기로 했구나!", LlmReplyEngine.clean("\"온이 : 영화 보러 가기로 했구나!\""))
        assertEquals("온이는 내 친구야", LlmReplyEngine.clean("온이는 내 친구야"))
    }

    @Test fun filtersSpecTestCases() {
        val spec = TestAssets.json("ai/llm/filters.json")!!.obj
        val f = OutputFilter(spec, people) // the app's wiring (AiModule): entities cases assume the tags.json people
        val bad = spec.objects("testCases").filter { c ->
            f.ok(c.str("rule")!!, c.str("text")!!, c.str("input") ?: "") != c.bool("pass")
        }
        assertTrue("failing filter cases: $bad", bad.isEmpty())
        assertEquals("4.2", OutputFilter.sinoValue("사 점 이"))
        assertEquals("2026", OutputFilter.sinoValue("이천이십육"))
        assertEquals("10", OutputFilter.sinoValue("십"))
    }

    private val people = TestAssets.json("ai/tags.json")!!.obj.objects("people").map { listOf(it.str("tag")!!) + it.strings("aliases") }

    @Test fun peopleDictionaryNamesMustBeInInput() {
        val f = OutputFilter(TestAssets.json("ai/llm/filters.json")!!.obj, people = listOf(listOf("엄마", "어머니"), listOf("동생"), listOf("부장님", "부장")))
        assertTrue(f.check(listOf("entities"), "엄마 말이 서운했겠다.", "엄마가 또 비교했다").isEmpty())
        assertTrue(f.check(listOf("entities"), "어머니 말이 서운했겠다.", "엄마가 또 비교했다").isEmpty()) // same tag
        assertEquals(listOf("entities"), f.check(listOf("entities"), "동생 때문에 속상했겠다.", "엄마가 또 비교했다"))
        assertTrue(f.check(listOf("entities"), "부장님이 그랬구나.", "부장이 또 뭐라 했다").isEmpty())
    }

    /** Integration-report false rejects (REPORT_v2 raw outputs): adverbs, words inside words, honorific variants. */
    @Test fun entitiesTolerateCommonWordsAndHonorifics() {
        val f = OutputFilter(TestAssets.json("ai/llm/filters.json")!!.obj, people)
        fun ok(out: String, inp: String) = f.check(listOf("entities"), out, inp).isEmpty()
        assertTrue(ok("한강에서 콩이랑 같이 노을 보는 시간 정말 평화로웠겠다.", "퇴근하고 콩이랑 한강공원 산책했다."))
        assertTrue(ok("그 마음 받아들여도 돼.", "요즘 아무것도 하기 싫다."))
        assertTrue(ok("원장님께 울컥하며 섭섭함을 느낌.", "원장쌤이 끝나고 따로 불러서 고생했다고 하셨다."))
        assertTrue(ok("버스를 30분이나 기다렸구나.", "버스를 삼십 분 기다렸다"))
        assertFalse(ok("5년 동안 다닌 학원이구나.", "3년 다닌 학원 오늘 마지막 날")) // '오늘'의 '오' is not 5
        assertFalse(ok("서연이랑 김치찌개 먹었구나.", "동기 지민이랑 김치찌개 먹었고"))
    }

    @Test fun crisisSpecTestCases() {
        val cfg = TestAssets.json("ai/crisis.json")!!.obj
        val d = CrisisDetector(cfg)
        val tc = cfg["testCases"]!!.obj
        val missed = tc.strings("positive").filterNot { d.detect(it).crisis }
        val fps = tc.strings("negative").filter { d.detect(it).crisis }
        assertTrue("missed: $missed", missed.isEmpty()) // recall must be 100%
        assertTrue("false positives: $fps", fps.size <= tc.strings("negative").size * 0.05)
        assertTrue(d.detect("다 그만하고 싶다. 아무도 내 편이 아닌 것 같고 내가 없어져도 아무도 모를 것 같다. 오늘도 책상 서랍에 있는 커터칼을 한참 쳐다봤다. 지난번처럼 팔에 하면 조금은 덜 답답할 것 같다.").crisis) // eval d25
        assertTrue(d.detect("ㅈㄱㅅㄷ 진짜").crisis) // 초성
        assertTrue(d.detect("죽 고 싶 다").crisis) // spacing
        assertFalse(d.detect("오늘 떡볶이 개맛있었다 ㅎㅎ").crisis)
    }

    @Test fun tokenizerAndDecisionMatchPythonReference() {
        val meta = TestAssets.json("ai/classifier/meta.json")!!.obj
        val tok = WordPieceTokenizer(TestAssets.json("ai/classifier/tokenizer.json")!!.obj, meta.int("maxLen")!!)
        val labels = TestAssets.json("ai/classifier/labels.json")!!.arr.map { it.jsonPrimitive.content }
        val clf = EmotionClassifier(null, tok, labels, meta)
        for (c in Json.parseToJsonElement(TestAssets.resource("classifier_reference.json")).jsonArray.map { it.jsonObject }) {
            val text = c.str("text")!!
            assertEquals(text, c["ids"]!!.jsonArray.map { it.jsonPrimitive.long }, tok.encode(text).toList())
            val probs = c["probs"]!!.jsonArray.map { it.jsonPrimitive.content.toDouble() }
            val expected = c["predict"]!!.jsonArray.map { it.jsonObject }.map { it.str("key") to it["intensity"]!!.jsonPrimitive.int }
            assertEquals(text, expected, clf.decide(text, probs).map { it.key.key to it.intensity })
        }
    }

    @Test fun classifierFallsBackToKeywordsWithoutModel() {
        val clf = EmotionClassifier(null, null, EmotionKey.entries.map { it.key }, null)
        assertFalse(clf.usesModel)
        assertEquals(EmotionKey.TIRED, clf.classify("그냥 피곤함").first().key)
        assertEquals(EmotionKey.UNSURE, clf.classify("밥 먹음").first().key)
    }

    @Test fun classifierUsesFakeModelLogits() {
        val labels = EmotionKey.entries.map { it.key }
        val logits = FloatArray(labels.size) { -5f }.also { it[labels.indexOf("tired")] = 2f }
        val meta = TestAssets.json("ai/classifier/meta.json")!!.obj
        val tok = WordPieceTokenizer(TestAssets.json("ai/classifier/tokenizer.json")!!.obj, 192)
        val clf = EmotionClassifier({ ids -> assertEquals(2L, ids.first()); logits }, tok, labels, meta)
        assertEquals(listOf(EntryEmotion(EmotionKey.TIRED, 5)), clf.classify("오늘 너무 피곤했다"))
    }

    @Test fun tagsFromDictionaryWithJosa() {
        val t = TagExtractor(TestAssets.json("ai/tags.json")!!.obj, userTags = listOf(Tag(TagKind.PLACE, "성수")))
        val tags = t.extract("엄마랑 카페에서 수다 떨고 민준이랑 성수에서 산책했다. 방학이라 좋다.")
        val values = tags.map { it.kind to it.value }
        assertTrue(values.toString(), TagKind.PERSON to "엄마" in values)
        assertTrue(values.toString(), TagKind.PLACE to "카페" in values)
        assertTrue(values.toString(), TagKind.ACTIVITY to "산책" in values)
        assertTrue(values.toString(), TagKind.PERSON to "민준" in values)
        assertTrue(values.toString(), TagKind.PLACE to "성수" in values)
        assertFalse(values.toString(), values.any { it.second == "방" }) // 방학 != 방
        assertTrue(tags.all { it.auto })
    }

    @Test fun tagsPeopleWithRangHagoWaGwa_placesKeepSurface() {
        val t = TagExtractor(TestAssets.json("ai/tags.json")!!.obj)
        fun of(text: String) = t.extract(text, max = 10).map { it.kind to it.value }
        val v = of("민지랑 한강 갔다가 수진하고 통화했다. 준혁과 서윤와 다음에 보기로 함")
        assertTrue(v.toString(), TagKind.PERSON to "민지" in v)
        assertTrue(v.toString(), TagKind.PERSON to "수진" in v)
        assertTrue(v.toString(), TagKind.PERSON to "준혁" in v)
        assertFalse(v.toString(), TagKind.PERSON to "서윤" in v) // 받침 있는 이름 + 와 is not Korean
        assertTrue(v.toString(), TagKind.PLACE to "한강" in v)
        assertFalse(v.toString(), TagKind.PLACE to "공원" in v)
        assertEquals("공원", t.category(Tag(TagKind.PLACE, "한강")))
        assertEquals("카페", t.category(Tag(TagKind.PLACE, "스벅")))
        assertEquals(listOf(TagKind.PERSON to "엄마"), of("울엄마랑 잤다")) // people still normalized
        // food / common nouns with 랑·와 are not people
        val food = of("우유랑 커피랑 고기와 치킨하고 먹었다. 어제와 같은 하루")
        assertTrue(food.toString(), food.none { it.first == TagKind.PERSON })
    }

    @Test fun commonWordsAreNotPeopleAliases() {
        // "같이"·"사람"·"타는" were split-off pieces of multi-word aliases and tagged 알바생/썸남 on everyday sentences.
        val t = TagExtractor(TestAssets.json("ai/tags.json")!!.obj)
        val v = t.extract("동생이랑 같이 영화 보러 가기로 했다. 사람이 많았다. 버스 타는 곳에서 만났다", max = 10).map { it.value }
        assertFalse(v.toString(), "알바생" in v || "썸남" in v)
        assertTrue(t.extract("썸 타는 사람이랑 밥 먹었다").any { it.value == "썸남" })
        val food = t.extract("친구랑 떡볶이를 먹고 민준이랑 통화했다", max = 10).map { it.value }
        assertFalse(food.toString(), "떡볶" in food)
        assertTrue(food.toString(), "민준" in food)
    }

    private val replies = Json.parseToJsonElement(TestAssets.resource("fixtures/replies.json")).jsonArray

    @Test fun templateFillsSlotsWithJosa() {
        assertEquals("민준이랑 놀았어", TemplateReplyEngine.fillSlot("{person}(이)랑 놀았어", "person", "민준"))
        assertEquals("엄마랑 놀았어", TemplateReplyEngine.fillSlot("{person}(이)랑 놀았어", "person", "엄마"))
        assertEquals("지수를 봤어", TemplateReplyEngine.fillSlot("{person}(을)를 봤어", "person", "지수"))
        assertEquals("민준이가 왔어", TemplateReplyEngine.fillSlot("{person}(이)가 왔어", "person", "민준"))
        assertEquals("학교로 갔어", TemplateReplyEngine.fillSlot("{place}(으)로 갔어", "place", "학교"))
        assertEquals("집으로 갔어", TemplateReplyEngine.fillSlot("{place}(으)로 갔어", "place", "집"))
        assertEquals("민준은 웃었어", TemplateReplyEngine.fillSlot("{person}(은)는 웃었어", "person", "민준"))
        assertEquals("엄마는 웃었어", TemplateReplyEngine.fillSlot("{person}(은)는 웃었어", "person", "엄마"))
        assertEquals("동생과", TemplateReplyEngine.fillSlot("{person}(과)와", "person", "동생"))
        assertEquals("엄마와", TemplateReplyEngine.fillSlot("{person}(과)와", "person", "엄마"))
        assertEquals("서울로 갔어", TemplateReplyEngine.fillSlot("{place}(으)로 갔어", "place", "서울"))

        val e = TemplateReplyEngine(replies, random = Random(1))
        val r = e.reply("민준이랑 영화", listOf(EntryEmotion(EmotionKey.JOY, 4)), listOf(Tag(TagKind.PERSON, "민준")))!!
        assertEquals("b2", r.id) // slot replies preferred when fillable
        assertEquals("민준이랑 보낸 시간이 하루를 데워 줬구나.", r.text)
    }

    @Test fun templateNoRepeatIntensityAndIrony() {
        val e = TemplateReplyEngine(replies, random = Random(2))
        val joy2 = listOf(EntryEmotion(EmotionKey.JOY, 2))
        assertEquals("b1", e.reply("좋았다", joy2, emptyList())!!.id) // b2 needs intensity >= 3 and a person
        val tired = listOf(EntryEmotion(EmotionKey.TIRED, 3))
        val place = listOf(Tag(TagKind.PLACE, "회사"))
        assertEquals("w1", e.reply("야근", tired, place, recentIds = setOf("w2"))!!.id) // 30-day no-repeat
        assertTrue(e.reply("야근", tired, place, recentIds = setOf("w1", "w2")) != null) // all used -> still answers
        // sarcasm marker + "joy" -> neutral reply instead of a cheerful one
        assertEquals("u1", e.reply("최고의 팀플이었다^^", listOf(EntryEmotion(EmotionKey.JOY, 4)), emptyList())!!.id)
        assertNotEquals("u1", e.reply("최고의 팀플이었다!", listOf(EntryEmotion(EmotionKey.JOY, 4)), emptyList())!!.id)
    }

    @Test fun modelsJsonParses() {
        val specs = ModelSpec.parse(TestAssets.json("ai/llm/models.json")!!.arr)
        assertEquals(listOf("e2b", "e4b"), specs.map { it.id })
        assertEquals(listOf(8, 12), specs.map { it.minRamGb })
        assertEquals(listOf(false, true), specs.map { it.premium })
        assertTrue(specs.all { it.url.startsWith("https://huggingface.co/litert-community/") && it.sha256!!.length == 64 && it.sizeBytes > 2e9 })
    }
}
