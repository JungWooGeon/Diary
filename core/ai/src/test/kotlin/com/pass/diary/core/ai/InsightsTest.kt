package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.SentenceMark
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTest {
    private fun sc(key: EmotionKey, p: Float) = EmotionScores(listOf(EntryEmotion(key)), mapOf(key to p))

    @Test fun marksStrongSentencesWithTheirRangeAndCapsTheShare() {
        val body = "엄마랑 싸웠다. 너무 속상해ㅠㅠ 그래도 저녁은 맛있었음!\n\n내일은 괜찮겠지"
        val scored = listOf(
            "엄마랑 싸웠다." to sc(EmotionKey.ANGRY, 0.7f),
            "너무 속상해ㅠㅠ" to sc(EmotionKey.SAD, 0.9f),
            "그래도 저녁은 맛있었음!" to sc(EmotionKey.JOY, 0.66f),
            "내일은 괜찮겠지" to sc(EmotionKey.HOPE, 0.3f),
        )
        // 3 pass 0.65 but 40 % of 4 sentences = 2: the strongest two, back in body order
        val m = Insights.marks(body, scored)
        assertEquals(listOf(SentenceMark(0, 8, EmotionKey.ANGRY), SentenceMark(9, 17, EmotionKey.SAD)), m)
        assertEquals("너무 속상해ㅠㅠ", body.substring(m[1].start, m[1].end))
        // keyword fallback has no probabilities; "잘 모르겠음" is never marked
        assertEquals(emptyList<SentenceMark>(), Insights.marks(body, listOf("엄마랑 싸웠다." to EmotionScores(listOf(EntryEmotion(EmotionKey.ANGRY)), emptyMap()))))
        assertEquals(emptyList<SentenceMark>(), Insights.marks(body, listOf("엄마랑 싸웠다." to sc(EmotionKey.UNSURE, 0.99f))))
    }

    @Test fun titleCandidates() {
        val body = "아침에 늦잠 잤다. 민지랑 한강에서 자전거 탔는데 바람이 너무 좋았다. 저녁엔 일찍 잤다."
        val vec = mapOf("아침에 늦잠 잤다." to floatArrayOf(1f, 0f), "민지랑 한강에서 자전거 탔는데 바람이 너무 좋았다." to floatArrayOf(0.8f, 0.6f), "저녁엔 일찍 잤다." to floatArrayOf(0f, 1f))
        val tags = listOf(Tag(TagKind.ACTIVITY, "자전거"), Tag(TagKind.PLACE, "한강"), Tag(TagKind.PERSON, "민지"))
        assertEquals(listOf("민지랑 한강에서 자전거 탔는데…", "민지 · 한강", "아침에 늦잠 잤다"), Insights.titles(body, tags) { vec[it] })
        assertEquals(listOf("아침에 늦잠 잤다"), Insights.titles(body, emptyList(), null)) // no model: first phrase only
        assertEquals(emptyList<String>(), Insights.titles("", emptyList(), null))
        assertEquals("개웃겼다", Insights.short("개웃겼다 ㅋㅋㅋ"))
    }
}
