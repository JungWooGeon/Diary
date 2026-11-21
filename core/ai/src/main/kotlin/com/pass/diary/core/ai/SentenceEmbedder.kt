package com.pass.diary.core.ai

import kotlin.math.sqrt

/** token ids [CLS] .. [SEP] -> L2-normalised sentence vector. Faked in unit tests. */
fun interface EmbeddingModel {
    fun embedding(inputIds: LongArray): FloatArray
}

/**
 * Text -> 256-d L2-normalised vector (cosine = dot product), from the emotion classifier's second output
 * (mean of layers 4–6, tools/classifier/export_tflite.py; quality numbers in tools/classifier/README.md).
 * Mid layers still know the situation ("엄마랑 싸움"), not only the feeling — but it is a 14M-param model:
 * for 뜻으로 검색 blend with [EmbeddingIndex.bigramOverlap] (see [EmbeddingIndex.searchScore]).
 */
class SentenceEmbedder(private val model: EmbeddingModel?, private val tokenizer: WordPieceTokenizer?) {
    val available get() = model != null && tokenizer != null

    /**
     * null when there is no model (keyword-fallback build), the text is blank, or inference fails.
     * Text past ~190 tokens is cut in chunks of whole sentences and averaged (weighted by length).
     */
    fun embed(text: String): FloatArray? {
        if (!available || text.isBlank()) return null
        return runCatching {
            var sum: FloatArray? = null
            for (chunk in chunks(text)) {
                val v = model!!.embedding(tokenizer!!.encode(chunk))
                val acc = sum ?: FloatArray(v.size).also { sum = it }
                for (i in v.indices) acc[i] += v[i] * chunk.length
            }
            sum!!.also { normalize(it) }
        }.getOrNull()
    }

    companion object {
        /** ~1.8 chars per token on diaries; the graph takes 192 tokens. */
        internal const val CHUNK_CHARS = 300
        private const val LONG_SENTENCE = 60

        private val BOUNDARY = Regex("(?<=[.!?…。~])\\s+|\\s*\\n+\\s*|(?<=[ㅋㅎㅠㅜ]{2})\\s+")
        // ponytail: diaries often skip punctuation; long runs are also cut after common plain-form endings. Grow from real misses.
        private val ENDING = Regex("(?<=[었았였했겠는싶같없있좋싫들]다|[요죠])\\s+")

        fun of(classifier: EmotionClassifier) =
            SentenceEmbedder((classifier.model as? LiteRtClassifierModel)?.takeIf { it.hasEmbedding }, classifier.tokenizer)

        /** Sentences of a diary body: split at . ! ? … ~, newlines, ㅋㅋ/ㅠㅠ runs; fragments without a letter are dropped. */
        fun splitSentences(body: String): List<String> =
            body.split(BOUNDARY)
                .flatMap { if (it.length > LONG_SENTENCE) it.split(ENDING) else listOf(it) }
                .map { it.trim() }
                .filter { s -> s.any { it.isLetterOrDigit() } }

        internal fun chunks(text: String): List<String> {
            if (text.length <= CHUNK_CHARS) return listOf(text)
            val out = ArrayList<String>(); val cur = StringBuilder()
            for (s in splitSentences(text)) {
                if (cur.isNotEmpty() && cur.length + 1 + s.length > CHUNK_CHARS) { out += cur.toString(); cur.clear() }
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s) // one sentence > CHUNK_CHARS stays whole; the tokenizer truncates it
            }
            if (cur.isNotEmpty()) out += cur.toString()
            return out
        }

        internal fun normalize(v: FloatArray) {
            val n = sqrt(v.fold(0f) { a, x -> a + x * x })
            if (n > 0f) for (i in v.indices) v[i] /= n
        }
    }
}
