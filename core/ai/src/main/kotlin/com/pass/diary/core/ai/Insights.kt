package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.SentenceMark
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import kotlin.math.ceil

/**
 * Thresholds for the emotion-AI screens (AI_GROWTH §2.1), measured with the shipped int8 model on
 * tools/classifier/data_src/embed_check.json (40 hand pairs, 25 queries) + tools/model-eval 30 diaries, 11/22.
 */
object Insights {
    /**
     * E4 비슷했던 날: cosine floor. Hand pairs: similar recall 0.55, different kept 0.05 (same feeling/other situation 0.10).
     * 30 diaries: 10 % of all pairs pass, 75 % of diaries still have a neighbour.
     */
    const val SIMILAR_MIN_COS = 0.75f

    /** E5: [EmbeddingIndex.searchScore] floor for "비슷한 내용". 25 queries: right diary kept 44 %, wrong ones 3 %. */
    const val SEARCH_MIN_SCORE = 0.40f

    /** E3: model probability of a sentence's top feeling. 128 diary sentences: 35 % pass, 28 of 30 diaries get one. */
    const val MARK_MIN_P = 0.65f
    /** ...and never more than this share of a diary's sentences, so a heavy day doesn't turn into a striped page. */
    const val MARK_MAX_SHARE = 0.4

    /** Entries shorter than this are left out of the index (a 1-tap mood, "피곤"): nothing to compare. */
    const val MIN_INDEX_CHARS = 8

    /** E3: (sentence, scores) in body order -> marks with char ranges in [body]. Keyword fallback (no probs) -> none. */
    fun marks(body: String, scored: List<Pair<String, EmotionScores>>): List<SentenceMark> {
        var from = 0
        val all = scored.mapNotNull { (s, sc) ->
            val start = body.indexOf(s, from).takeIf { it >= 0 } ?: return@mapNotNull null
            from = start + s.length
            val key = sc.top.key.takeIf { it != EmotionKey.UNSURE } ?: return@mapNotNull null
            val p = sc.probs[key] ?: return@mapNotNull null
            SentenceMark(start, start + s.length, key) to p
        }
        val cap = ceil(scored.size * MARK_MAX_SHARE).toInt()
        return all.filter { it.second >= MARK_MIN_P }.sortedByDescending { it.second }.take(cap).map { it.first }.sortedBy { it.start }
    }

    /**
     * E6 제목 후보: the most typical sentence (closest to the mean of all sentence vectors), the found tags, then the first
     * phrase. Distinct, at most 3. [embed] null (no model) = no central sentence.
     */
    fun titles(body: String, tags: List<Tag>, embed: ((String) -> FloatArray?)?): List<String> {
        val sentences = SentenceEmbedder.splitSentences(body).filterNot { it.startsWith("Q.") }
        val central = if (embed == null || sentences.size < 2) null else {
            val vs = sentences.take(MAX_TITLE_SENTENCES).mapNotNull { s -> embed(s)?.let { s to it } }
            vs.getOrNull(EmbeddingIndex.centralIndex(vs.map { it.second }))?.first
        }
        // who and where say more than what ("민지 · 성수" over "사진 · 약속")
        val fromTags = tags.sortedBy { listOf(TagKind.PERSON, TagKind.PLACE, TagKind.ACTIVITY).indexOf(it.kind).let { i -> if (i < 0) 3 else i } }
            .take(2).joinToString(" · ") { it.value }.takeIf { tags.isNotEmpty() }
        return listOfNotNull(central?.let(::short), fromTags, sentences.firstOrNull()?.let(::short)).distinct().take(3)
    }

    /** ≤ [max] chars, cut at a word, trailing punctuation dropped. */
    fun short(s: String, max: Int = 18): String {
        val t = s.trim().trimEnd('.', '!', '?', '~', '…', ' ').replace(Regex("[ㅋㅎㅠㅜ]{2,}$"), "").trim()
        if (t.length <= max) return t
        return t.take(max).substringBeforeLast(' ').ifBlank { t.take(max) } + "…"
    }

    // ponytail: each sentence ~75 ms on a Note20; past 12 the first ones decide. Sample evenly if long diaries title badly.
    private const val MAX_TITLE_SENTENCES = 12
}
