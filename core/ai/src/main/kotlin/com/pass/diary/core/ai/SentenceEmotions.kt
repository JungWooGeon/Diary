package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import kotlin.math.exp

/**
 * One sentence's feeling (문장별 감정 하이라이트). [emotions] = the same decision as [EmotionClassifier.classify];
 * [probs] = model sigmoid per key before the lexicon boost (empty when the keyword fallback ran).
 */
data class EmotionScores(val emotions: List<EntryEmotion>, val probs: Map<EmotionKey, Float>) {
    val top: EntryEmotion get() = emotions.first()
}

/** Body -> (sentence, scores) in order. One model run per sentence (~75 ms each on a Note20): call off the main thread. */
fun EmotionClassifier.classifySentences(body: String): List<Pair<String, EmotionScores>> =
    SentenceEmbedder.splitSentences(body).map { it to scores(it) }

internal fun EmotionClassifier.scores(text: String): EmotionScores {
    val logits = if (usesModel) runCatching { model!!.logits(tokenizer!!.encode(text)) }.getOrNull() else null
    logits ?: return EmotionScores(classify(text), emptyMap())
    val p = logits.map { 1.0 / (1.0 + exp(-it.toDouble())) }
    val probs = labels.zip(p).mapNotNull { (k, v) -> EmotionKey.of(k)?.let { it to v.toFloat() } }.toMap()
    return EmotionScores(decide(text, p), probs)
}
