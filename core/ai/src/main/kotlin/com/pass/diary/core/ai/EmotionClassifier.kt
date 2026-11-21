package com.pass.diary.core.ai

import android.os.SystemClock
import android.util.Log
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter
import kotlin.math.exp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * BERT WordPiece tokenizer reading HF `tokenizer.json` (BertNormalizer, lowercase=false, BertPreTokenizer,
 * [CLS] A [SEP]). Matches `tokenizers` output for shared/ai/classifier/tokenizer.json (see classifier_reference.json test).
 */
class WordPieceTokenizer(tokenizerJson: JsonObject, private val maxLen: Int) {
    private val model = tokenizerJson["model"]!!.obj
    private val vocab: Map<String, Int> = model["vocab"]!!.obj.mapValues { it.value.jsonPrimitive.int }
    private val unk = vocab.getValue(model.str("unk_token") ?: "[UNK]")
    private val prefix = model.str("continuing_subword_prefix") ?: "##"
    private val maxChars = model.int("max_input_chars_per_word") ?: 100
    private val cls = vocab.getValue("[CLS]")
    private val sep = vocab.getValue("[SEP]")
    private val lowercase = tokenizerJson["normalizer"]?.obj?.bool("lowercase") ?: false

    fun encode(text: String): LongArray {
        val ids = ArrayList<Int>()
        for (word in preTokenize(normalize(text))) {
            ids += wordPiece(word)
            if (ids.size >= maxLen - 2) break
        }
        return (listOf(cls) + ids.take(maxLen - 2) + sep).map { it.toLong() }.toLongArray()
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i); i += Character.charCount(cp)
            when {
                cp == 0 || cp == 0xFFFD || (isControl(cp) && !isWhitespace(cp)) -> Unit
                isWhitespace(cp) -> sb.append(' ')
                isCjk(cp) -> sb.append(' ').appendCodePoint(cp).append(' ')
                else -> sb.appendCodePoint(cp)
            }
        }
        return if (lowercase) sb.toString().lowercase() else sb.toString()
    }

    private fun preTokenize(text: String): List<String> {
        val out = ArrayList<String>(); val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i); i += Character.charCount(cp)
            when {
                cp == ' '.code -> flush()
                isPunct(cp) -> { flush(); out += String(Character.toChars(cp)) }
                else -> cur.appendCodePoint(cp)
            }
        }
        flush()
        return out
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.codePointCount(0, word.length) > maxChars) return listOf(unk)
        val out = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length; var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) prefix else "") + word.substring(start, end)
                vocab[piece]?.let { found = it }
                if (found != null) break
                end = word.offsetByCodePoints(end, -1)
            }
            if (found == null) return listOf(unk)
            out += found!!; start = end
        }
        return out
    }

    private fun isWhitespace(cp: Int) = cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code ||
        Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()
    private fun isControl(cp: Int) = cp != '\t'.code && cp != '\n'.code && cp != '\r'.code &&
        Character.getType(cp).let { it == Character.CONTROL.toInt() || it == Character.FORMAT.toInt() }
    private fun isCjk(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF ||
        cp in 0x2A700..0x2B73F || cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF || cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F
    private fun isPunct(cp: Int) = cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 ||
        Character.getType(cp).let {
            it == Character.CONNECTOR_PUNCTUATION.toInt() || it == Character.DASH_PUNCTUATION.toInt() ||
                it == Character.START_PUNCTUATION.toInt() || it == Character.END_PUNCTUATION.toInt() ||
                it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                it == Character.OTHER_PUNCTUATION.toInt()
        }
}

/** The classifier graph: token ids [CLS] .. [SEP] (<= maxLen) -> 21 logits. Faked in unit tests. */
fun interface ClassifierModel {
    fun logits(inputIds: LongArray): FloatArray
}

/**
 * LiteRT (TFLite) model.tflite, signature "classify": int32 input_ids + attention_mask [1, maxLen] -> logits [1, 21] + embedding [1, 256].
 * The graph has one fixed length, so ids are right-padded with 0 and masked out (tools/classifier/export_tflite.py).
 * One run gives both outputs; [SentenceEmbedder] shares this instance (one Interpreter, one copy of the weights).
 */
class LiteRtClassifierModel(modelBytes: ByteArray) : ClassifierModel, EmbeddingModel, AutoCloseable {
    private val interpreter = Interpreter(
        ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).put(modelBytes).apply { rewind() },
    )
    private val len = interpreter.getInputTensorFromSignature("input_ids", SIGNATURE).shape()[1]
    private val outputs = interpreter.getSignatureOutputs(SIGNATURE).toList()
    private val logitsName = if ("logits" in outputs) "logits" else outputs.single() // pre-embedding model: one "output_0"
    val hasEmbedding get() = "embedding" in outputs

    @Synchronized // one Interpreter = one set of buffers
    private fun run(inputIds: LongArray): Map<String, FloatArray> {
        val ids = IntArray(len); val mask = IntArray(len)
        for (i in 0 until minOf(len, inputIds.size)) { ids[i] = inputIds[i].toInt(); mask[i] = 1 }
        val out = outputs.associateWith { arrayOf(FloatArray(interpreter.getOutputTensorFromSignature(it, SIGNATURE).shape()[1])) }
        interpreter.runSignature(mapOf("input_ids" to arrayOf(ids), "attention_mask" to arrayOf(mask)), out, SIGNATURE)
        return out.mapValues { it.value[0] }
    }

    override fun logits(inputIds: LongArray): FloatArray = run(inputIds).getValue(logitsName)
    override fun embedding(inputIds: LongArray): FloatArray = run(inputIds).getValue("embedding")

    override fun close() = interpreter.close()

    private companion object { const val SIGNATURE = "classify" }
}

/**
 * Emotion suggestion (ARCHITECTURE §5.1 ②). Decision rule = shared/ai/classifier/meta.json, mirrors tools/classifier/infer.py.
 * If the model files are missing (or LiteRT fails to load), falls back to [KeywordEmotionHeuristic].
 */
class EmotionClassifier(
    internal val model: ClassifierModel?,
    internal val tokenizer: WordPieceTokenizer?,
    internal val labels: List<String>,
    meta: JsonObject?,
    private val fallback: KeywordEmotionHeuristic = KeywordEmotionHeuristic(),
) {
    private val thresholds = meta?.get("thresholds")?.obj?.mapValues { it.value.jsonPrimitive.content.toDouble() } ?: emptyMap()
    private val unsureFloor = meta?.dbl("unsureFloor") ?: 0.1
    private val lexicon = meta?.get("lexicon")?.obj?.mapValues { Regex(it.value.jsonPrimitive.content) } ?: emptyMap()
    private val lexiconBoost = meta?.dbl("lexiconBoost") ?: 0.0
    private val rule = meta?.get("intensityRule")?.obj
    private val pLow = rule?.dbl("pLow") ?: 0.05
    private val pHigh = rule?.dbl("pHigh") ?: 0.5
    private val base = rule?.int("base") ?: 3
    private val span = rule?.dbl("span") ?: 1.0
    private val boost = rule?.str("boost")?.let(::Regex)
    private val soften = rule?.str("soften")?.let(::Regex)

    val usesModel get() = model != null && tokenizer != null

    fun classify(text: String): List<EntryEmotion> {
        if (!usesModel || text.isBlank()) return fallback.classify(text)
        val t0 = SystemClock.elapsedRealtime()
        val logits = runCatching { model!!.logits(tokenizer!!.encode(text)) }.getOrElse { return fallback.classify(text) }
        Log.d(TAG, "inference ${SystemClock.elapsedRealtime() - t0}ms")
        return decide(text, logits.map { 1.0 / (1.0 + exp(-it.toDouble())) })
    }

    /** Mirrors tools/classifier/infer.py `boosted` + `predict` (meta.json lexiconRule / decision / intensityRule). */
    internal fun decide(text: String, probs: List<Double>): List<EntryEmotion> {
        val p = probs.toMutableList()
        for ((k, rx) in lexicon) {
            val hit = rx.find(text) ?: continue
            if (NEGATED.containsMatchIn(text.substring(maxOf(0, hit.range.first - 2), hit.range.first))) continue
            val i = labels.indexOf(k).takeIf { it >= 0 } ?: continue
            p[i] = minOf(0.99, p[i] + lexiconBoost)
        }
        val unsureIdx = labels.indexOf("unsure")
        val top = labels.indices.filter { it != unsureIdx }.maxBy { p[it] }
        if (p[top] < unsureFloor || (unsureIdx >= 0 && p[unsureIdx] > p[top])) return listOf(EntryEmotion(EmotionKey.UNSURE, 3))
        val adj = (if (boost?.containsMatchIn(text) == true) 1 else 0) - (if (soften?.containsMatchIn(text) == true) 1 else 0)
        return labels.indices.filter { it != unsureIdx && (p[it] >= (thresholds[labels[it]] ?: 0.5) || it == top) }
            .sortedByDescending { p[it] }.take(3)
            .map { i ->
                val x = ((p[i] - pLow) / (pHigh - pLow)).coerceIn(0.0, 1.0)
                val intensity = (base + Math.rint(span * x).toInt() + adj).coerceIn(1, 5) // rint = Python round (half-even)
                EntryEmotion(EmotionKey.of(labels[i])!!, intensity)
            }
    }

    companion object {
        private const val TAG = "EmotionClassifier"
        private val NEGATED = Regex("(안|않|못|없)\\s?$")

        fun load(assets: AssetSource): EmotionClassifier {
            val meta = assets.json("ai/classifier/meta.json")?.obj
            val labels = assets.json("ai/classifier/labels.json")?.arr?.map { it.jsonPrimitive.content }
                ?: EmotionKey.entries.map { it.key }
            val tok = assets.json("ai/classifier/tokenizer.json")?.obj?.let { WordPieceTokenizer(it, meta?.int("maxLen") ?: 192) }
            val model = assets.bytes("ai/classifier/model.tflite")?.let { bytes ->
                runCatching { LiteRtClassifierModel(bytes) }.onFailure { Log.w(TAG, "LiteRT load failed, keyword fallback", it) }.getOrNull()
            }
            return EmotionClassifier(model, tok, labels, meta)
        }
    }
}

/** Fallback when the classifier model is missing: keyword counts per emotion. ponytail: tiny lexicon, grow from real misses. */
class KeywordEmotionHeuristic {
    private val lexicon: Map<EmotionKey, Regex> = mapOf(
        EmotionKey.JOY to "기쁘|기뻤|좋았|행복|최고|신나|ㅎㅎ|맛있",
        EmotionKey.PROUD to "뿌듯|해냈|성공|합격|칭찬해",
        EmotionKey.EXCITED to "신났|신난|설레|대박|!!",
        EmotionKey.GRATEFUL to "고마|감사|감동",
        EmotionKey.COMFY to "편안|편했|여유|느긋|포근",
        EmotionKey.RELIEVED to "다행|안심|안도",
        EmotionKey.LIGHT to "홀가분|후련|시원섭섭",
        EmotionKey.FLUTTER to "설렘|두근|심장",
        EmotionKey.LOVE to "사랑|좋아해",
        EmotionKey.HOPE to "기대|기다려",
        EmotionKey.MISS to "보고 ?싶|그립|그리워|생각났",
        EmotionKey.SAD to "슬프|슬펐|울었|눈물|ㅠㅠ|ㅜㅜ|😭",
        EmotionKey.DOWN to "우울|무기력|꿀꿀|아무것도 하기 ?싫",
        EmotionKey.LONELY to "외로|혼자|쓸쓸|허전",
        EmotionKey.HURT to "서운|섭섭|속상|읽씹",
        EmotionKey.TIRED to "피곤|지쳤|지친|졸려|힘들|녹초|🫠",
        EmotionKey.ANXIOUS to "불안|걱정|떨려|긴장|초조|어떡하",
        EmotionKey.ANNOYED to "짜증|귀찮|빡|ㅅㅂ|답답",
        EmotionKey.ANGRY to "화나|화났|열받|분노|억울",
    ).mapValues { Regex(it.value) }

    fun classify(text: String): List<EntryEmotion> {
        val scored = lexicon.mapValues { it.value.findAll(text).count() }.filterValues { it > 0 }
        if (scored.isEmpty()) return listOf(EntryEmotion(EmotionKey.UNSURE, 3))
        return scored.entries.sortedByDescending { it.value }.take(3)
            .map { EntryEmotion(it.key, (2 + it.value).coerceAtMost(5)) }
    }
}
