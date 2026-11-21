package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingTest {
    private val tok = WordPieceTokenizer(TestAssets.json("ai/classifier/tokenizer.json")!!.obj, 192)
    private fun unit(vararg x: Float) = x.copyOf().also { SentenceEmbedder.normalize(it) }

    @Test fun splitsDiaryIntoSentences() {
        assertEquals(
            listOf("엄마랑 싸웠다.", "너무 속상해ㅠㅠ", "그래도 저녁은 맛있었음!", "내일은 괜찮겠지"),
            SentenceEmbedder.splitSentences("엄마랑 싸웠다. 너무 속상해ㅠㅠ 그래도 저녁은 맛있었음!\n\n내일은 괜찮겠지"),
        )
        assertEquals(listOf("개웃겼다 ㅋㅋㅋ", "근데 피곤"), SentenceEmbedder.splitSentences("개웃겼다 ㅋㅋㅋ 근데 피곤"))
        // no punctuation: only long runs are cut at plain-form endings, short ones stay whole ("바다 보러" is not an ending)
        assertEquals(listOf("바다 보러 갔다 좋았다"), SentenceEmbedder.splitSentences("바다 보러 갔다 좋았다"))
        val long = "오늘 아침에 늦잠 자서 버스를 놓쳤고 결국 지각했다 " + "점심은 친구랑 학식에서 돈까스 먹었는데 생각보다 맛있었다 " +
            "저녁엔 집에 와서 과제하다가 유튜브만 보다 잠들었다"
        assertEquals(3, SentenceEmbedder.splitSentences(long).size)
        assertEquals(emptyList<String>(), SentenceEmbedder.splitSentences(" ... \n ~ "))
    }

    @Test fun embedNormalisesAndAveragesLongTextChunks() {
        val seen = ArrayList<LongArray>()
        val e = SentenceEmbedder({ ids -> seen += ids; floatArrayOf(3f, 4f) }, tok)
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), e.embed("오늘 너무 피곤했다. 그래도 운동은 했다."), 1e-6f)
        assertEquals(2L, seen.single().first()) // [CLS]
        val body = List(30) { "오늘은 친구랑 카페에 가서 한참 수다를 떨었다." }.joinToString(" ")
        assertTrue(body.length > SentenceEmbedder.CHUNK_CHARS)
        seen.clear()
        val v = e.embed(body)!!
        assertTrue(seen.size >= 2 && seen.all { it.size <= 192 })
        assertEquals(1f, sqrt(v[0] * v[0] + v[1] * v[1]), 1e-5f)
        assertNull(e.embed("  "))
        assertNull(SentenceEmbedder(null, tok).embed("오늘"))
        assertNull(SentenceEmbedder({ error("boom") }, tok).embed("오늘"))
    }

    @Test fun embedderNeedsTheEmbeddingModel() {
        val clf = EmotionClassifier({ FloatArray(21) }, tok, EmotionKey.entries.map { it.key }, null)
        assertEquals(false, SentenceEmbedder.of(clf).available) // a plain ClassifierModel (tests, keyword fallback) has no embedding
    }

    @Test fun indexTopKExcludeAndBlend() {
        val idx = EmbeddingIndex()
        idx.put("a", unit(1f, 0f)); idx.put("b", unit(1f, 1f)); idx.put("c", unit(0f, 1f))
        assertEquals(listOf("a", "b"), idx.topK(unit(1f, 0.1f), 2).map { it.first })
        assertEquals(listOf("b", "c"), idx.topK(idx["a"]!!, 2, exclude = setOf("a")).map { it.first })
        assertEquals("c", idx.topK(unit(1f, 0f), 1) { id, cos -> if (id == "c") 9f else cos }.single().first)
        idx.remove("b"); assertEquals(setOf("a", "c"), idx.ids())
        idx.put("a", unit(0f, 1f)); assertEquals(2, idx.size)
    }

    @Test fun indexRoundTripsAndRejectsOtherVersion() {
        val idx = EmbeddingIndex().apply { put("2026-11-22 일기", unit(0.3f, -0.4f, 0.5f)); put("x", unit(1f, 2f, 3f), stamp = -7) }
        val bytes = ByteArrayOutputStream().also { idx.write(it) }.toByteArray()
        val back = EmbeddingIndex.read(ByteArrayInputStream(bytes))!!
        assertEquals(idx.ids(), back.ids())
        assertArrayEquals(idx["x"], back["x"], 0f)
        assertEquals(-7, back.stamp("x")); assertEquals(0, back.stamp("2026-11-22 일기"))
        bytes[3] = (EmbeddingIndex.VERSION + 1).toByte()
        assertNull(EmbeddingIndex.read(ByteArrayInputStream(bytes)))
        assertNull(EmbeddingIndex.read(ByteArrayInputStream(byteArrayOf(0, 0))))
    }

    @Test fun bigramOverlapAndCentral() {
        assertEquals(1f, EmbeddingIndex.bigramOverlap("엄마랑", "오늘 엄마랑 싸웠다"), 0f)
        assertEquals(0.5f, EmbeddingIndex.bigramOverlap("엄마 싸움", "엄마가 또 비교했다"), 0f) // "엄마" hit, "싸움" miss
        assertEquals(0f, EmbeddingIndex.bigramOverlap("", "아무거나"), 0f)
        assertEquals(1, EmbeddingIndex.centralIndex(listOf(unit(1f, 0f), unit(1f, 1f), unit(0f, 1f))))
        assertEquals(-1, EmbeddingIndex.centralIndex(emptyList()))
    }

    @Test fun classifySentencesGivesEachSentenceItsFeeling() {
        val labels = EmotionKey.entries.map { it.key }
        val meta = TestAssets.json("ai/classifier/meta.json")!!.obj
        val tired = tok.encode("피곤했다.").toList()
        val clf = EmotionClassifier({ ids ->
            FloatArray(labels.size) { -5f }.also { it[labels.indexOf(if (ids.toList() == tired) "tired" else "joy")] = 2f }
        }, tok, labels, meta)
        val r = clf.classifySentences("피곤했다. 그래도 떡볶이 먹어서 좋았다!")
        assertEquals(listOf("피곤했다.", "그래도 떡볶이 먹어서 좋았다!"), r.map { it.first })
        assertEquals(EmotionKey.TIRED, r[0].second.top.key)
        assertEquals(EmotionKey.JOY, r[1].second.top.key)
        assertEquals(0.88f, r[1].second.probs[EmotionKey.JOY]!!, 0.01f)
        val noModel = EmotionClassifier(null, null, labels, null).classifySentences("그냥 피곤함. 밥 먹음")
        assertEquals(listOf(EmotionKey.TIRED, EmotionKey.UNSURE), noModel.map { it.second.top.key })
        assertTrue(noModel.all { it.second.probs.isEmpty() })
    }
}
