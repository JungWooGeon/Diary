package com.pass.diary.core.ai

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Entry id -> [SentenceEmbedder] vector, cosine top-k. Pure Kotlin, in memory; vectors stay on the device.
 * Persist with [write]/[read] (e.g. filesDir/ai/embeddings.bin); it is derived data, so on a missing file or
 * a [VERSION] change, rebuild from the diaries. ponytail: linear scan (~1k entries x 256 floats < 1 ms); ANN only past ~50k.
 *
 * - 비슷했던 날: `topK(index[id]!!, 3, exclude = setOf(id))`
 * - 뜻으로 검색: `topK(embed(q)!!, 3) { id, cos -> searchScore(cos, bigramOverlap(q, bodyOf(id))) }`
 * - 이번 달의 한 줄: [centralIndex] over that month's sentence vectors
 */
class EmbeddingIndex {
    private val vectors = LinkedHashMap<String, FloatArray>()
    private val stamps = HashMap<String, Int>()

    /** [stamp] = what the vector was made from (e.g. the text's hash), so a caller can tell a stale one. */
    @Synchronized fun put(id: String, v: FloatArray, stamp: Int = 0) { vectors[id] = v; stamps[id] = stamp }
    @Synchronized fun remove(id: String) { vectors.remove(id); stamps.remove(id) }
    @Synchronized fun stamp(id: String): Int? = stamps[id]
    @Synchronized operator fun get(id: String): FloatArray? = vectors[id]
    @Synchronized fun ids(): Set<String> = vectors.keys.toSet()
    val size: Int @Synchronized get() = vectors.size

    /** Best first. [score] defaults to cosine; pass one to blend in other signals. */
    @Synchronized
    fun topK(
        query: FloatArray,
        k: Int,
        exclude: Set<String> = emptySet(),
        score: (id: String, cos: Float) -> Float = { _, cos -> cos },
    ): List<Pair<String, Float>> =
        vectors.entries.asSequence().filter { it.key !in exclude }
            .map { (id, v) -> id to score(id, dot(query, v)) }
            .sortedByDescending { it.second }.take(k).toList()

    /** [VERSION], count, then (id UTF, stamp, dim, floats). */
    @Synchronized
    fun write(out: OutputStream) {
        val d = DataOutputStream(out.buffered())
        d.writeInt(VERSION); d.writeInt(vectors.size)
        for ((id, v) in vectors) { d.writeUTF(id); d.writeInt(stamps[id] ?: 0); d.writeInt(v.size); v.forEach(d::writeFloat) }
        d.flush()
    }

    companion object {
        /** Bump when model.tflite's embedding (or this format) changes: old files then read as null -> rebuild. 2: stamps. */
        const val VERSION = 2
        /** Weight of [bigramOverlap] in 뜻으로 검색 (tools/classifier/embed_check.py: 0.3–0.5 best on 25 queries). */
        const val LEXICAL_WEIGHT = 0.4f

        /** null on another [VERSION] or a broken file. */
        fun read(input: InputStream): EmbeddingIndex? = runCatching {
            val d = DataInputStream(input.buffered())
            if (d.readInt() != VERSION) return null
            EmbeddingIndex().apply { repeat(d.readInt()) { val id = d.readUTF(); val st = d.readInt(); put(id, FloatArray(d.readInt()) { d.readFloat() }, st) } }
        }.getOrNull()

        fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in 0 until minOf(a.size, b.size)) s += a[i] * b[i]
            return s
        }

        /** Share of the query's char bigrams (per word) found in the text, 0..1. Mirrors embed_check.py `overlap`. */
        fun bigramOverlap(query: String, text: String): Float {
            val q = bigrams(query)
            if (q.isEmpty()) return 0f
            val t = bigrams(text)
            return q.count { it in t }.toFloat() / q.size
        }

        fun searchScore(cos: Float, overlap: Float) = (1 - LEXICAL_WEIGHT) * cos + LEXICAL_WEIGHT * overlap

        /** Index of the vector closest to the mean of all: the most "typical" one (이번 달의 한 줄). -1 if empty. */
        fun centralIndex(vectors: List<FloatArray>): Int {
            if (vectors.isEmpty()) return -1
            val mean = FloatArray(vectors[0].size)
            for (v in vectors) for (i in mean.indices) mean[i] += v[i]
            return vectors.indices.maxBy { dot(mean, vectors[it]) }
        }

        private fun bigrams(s: String): Set<String> =
            s.split(Regex("\\s+")).filter { it.isNotEmpty() }
                .flatMapTo(HashSet()) { w -> if (w.length < 2) listOf(w) else (0 until w.length - 1).map { w.substring(it, it + 2) } }
    }
}
