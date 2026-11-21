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

    @Test fun everydayVerbsAreNotActivityAliases() {
        // "보고"(보다) tagged 회의 and "중간에" tagged 시험 on ordinary sentences.
        val t = TagExtractor(TestAssets.json("ai/tags.json")!!.obj)
        val v = t.extract("민지랑 전시 보고 카페 갔다. 중간에 잠깐 쉬었다", max = 10).map { it.value }
        assertFalse(v.toString(), "회의" in v || "시험" in v)
        assertTrue(t.extract("보고서 쓰느라 바빴다").any { it.value == "회의" })
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

}
