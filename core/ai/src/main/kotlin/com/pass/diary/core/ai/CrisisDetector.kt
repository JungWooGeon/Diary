package com.pass.diary.core.ai

import java.text.Normalizer
import kotlinx.serialization.json.JsonObject

/**
 * Rule-based crisis detector over shared/ai/crisis.json (reference: tools/classifier/test_crisis.py).
 * Runs first in [AiPipeline]; a hit stops every other AI step. Ambiguous -> err toward showing help.
 */
class CrisisDetector(cfg: JsonObject) {
    private val lowercase = cfg["normalize"]?.obj?.bool("lowercase") ?: true
    private val remove = Regex(cfg["normalize"]!!.obj.str("removeRegex")!!)
    private val excludes = cfg.objects("excludes").map { Regex(it.str("regex")!!) }
    private val patterns = cfg.objects("patterns").map { Triple(it.str("id")!!, Regex(it.str("regex")!!), it.dbl("weight") ?: 1.0) }
    private val threshold = cfg.dbl("threshold") ?: 1.0

    data class Result(val crisis: Boolean, val score: Double, val hits: List<String>)

    /** NFC first so decomposed jamo (some keyboards/STT) match the same as composed syllables and 초성. */
    fun normalize(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFC)
        if (lowercase) t = t.lowercase()
        t = t.replace(remove, "")
        for (e in excludes) t = t.replace(e, "")
        return t
    }

    fun detect(text: String): Result {
        val t = normalize(text)
        val hits = patterns.filter { it.second.containsMatchIn(t) }
        val score = hits.sumOf { it.third }
        return Result(score >= threshold, score, hits.map { it.first })
    }
}
