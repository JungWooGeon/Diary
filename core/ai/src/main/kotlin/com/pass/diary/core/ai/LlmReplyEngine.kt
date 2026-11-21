package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Intensity
import com.pass.diary.core.model.ReplySource
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** shared/ai/llm/prompts.json, one [Task] per key (reply, emotions, tidy, title, summary, retro, question, sarcasm, chat.ask, chat.compose). */
class LlmPrompts(val root: JsonObject) {
    class Task(val cfg: JsonObject, val system: String = cfg.str("system")!!) {
        val filters = cfg.strings("filters")
        val fewshot = cfg.objects("fewshot").map { it.str("user")!! to it.str("assistant")!! }
        val schema: String? = cfg["schema"]?.toString()
        val sampling = cfg["sampling"]!!.obj.let {
            Sampling(it.dbl("temperature") ?: 0.5, it.int("topK") ?: 40, it.dbl("topP") ?: 0.95, it.int("maxTokens") ?: 256)
        }
    }

    fun task(path: String): Task = Task(path.split('.').fold(root) { o, k -> o[k]!!.obj })
    val reply = task("reply")
    val replyHints = root["reply"]!!.obj["hints"]!!.obj
    val sarcasmPrefilter = Regex(root["sarcasm"]!!.obj.str("prefilter")!!)
    val nameToKey: Map<String, String> = root["emotions"]!!.obj["nameToKey"]!!.obj.mapValues { it.value.jsonPrimitive.content }
    val synonyms: Map<String, String> = root["emotions"]!!.obj["synonyms"]!!.obj.mapValues { it.value.jsonPrimitive.content }
}

/**
 * No streaming: the reply is generated, filtered and (if needed) retried behind the "답장 쓰는 중" animation, so
 * text the filter rejects is never on screen. The UI reveals the accepted text with a typewriter.
 */
sealed interface ReplyEvent {
    data class Done(val text: String, val source: ReplySource, val templateId: String?) : ReplyEvent
}

/**
 * LLM tasks with the quality gates of ARCHITECTURE §5.3: post-filter -> retry once -> fallback
 * (template reply / original text / null). Everything runs on-device through [runtime].
 */
class LlmReplyEngine(
    private val runtime: LlmRuntime,
    private val prompts: LlmPrompts,
    private val filter: OutputFilter,
    private val template: TemplateReplyEngine,
    private val timeoutMs: Long = 30_000,
) {
    suspend fun warmUp() = runtime.warmUp()

    /** ARCHITECTURE §5.1 ④. [userMood] = emotion the user picked themselves (strongest hint against irony misreads). */
    fun reply(
        text: String,
        emotions: List<EntryEmotion>,
        tags: List<Tag>,
        recentTemplateIds: Set<String> = emptySet(),
        userMood: EmotionKey? = null,
    ): Flow<ReplyEvent.Done> = flow {
        // A mood the user picked from a bright/calm/flutter group overrides the sarcasm reader (its false positives
        // turn sincere thanks into "you must be upset": REPORT_v2 held-out 3/6).
        val positivePick = userMood?.group?.key in setOf("bright", "calm", "flutter")
        val hint = buildString {
            if (!positivePick) readingCache(text)?.let { append(it) }
            userMood?.let { append(prompts.replyHints.str("mood")!!.replace("{mood}", it.label)) }
        }
        val out = generateChecked(prompts.reply, text + hint, input = text)
        if (out != null) emit(ReplyEvent.Done(out, ReplySource.LLM, null))
        else {
            val t = template.reply(text, emotions, tags, recentTemplateIds)
            emit(ReplyEvent.Done(t?.text ?: FALLBACK, ReplySource.TEMPLATE, t?.id))
        }
    }

    private var lastReading: Pair<String, String?>? = null

    /** reply() and summary() run on the same save; read once. */
    private suspend fun readingCache(text: String): String? =
        lastReading?.takeIf { it.first == text }?.second ?: sarcasmReading(text).also { lastReading = text to it }

    /** The reading step (prompts.json "sarcasm"): only when the diary contains praise words. Returns the hint or null. */
    internal suspend fun sarcasmReading(text: String): String? {
        if (!prompts.sarcasmPrefilter.containsMatchIn(text)) return null
        val r = json(prompts.task("sarcasm"), text) ?: return null
        val bad = r.strings("bad")
        if (r.bool("sarcasm") != true || bad.isEmpty()) return null
        val q = { xs: List<String> -> xs.joinToString(", ") { "'$it'" } }
        return prompts.replyHints.str("sarcasm")!!
            .replace("{praise}", q(r.strings("praise").ifEmpty { listOf("좋은 말") })).replace("{bad}", q(bad))
    }

    /** Emotion JSON (constrained). Unknown names are mapped through synonyms; null if the model fails. */
    suspend fun emotions(text: String): Pair<List<EntryEmotion>, List<Tag>>? {
        val o = json(prompts.task("emotions"), text) ?: return null
        val emos = o.objects("emotions").mapNotNull { e ->
            val name = e.str("name")?.let { prompts.synonyms[it] ?: it } ?: return@mapNotNull null
            val key = prompts.nameToKey[name]?.let(EmotionKey::of) ?: return@mapNotNull null
            EntryEmotion(key, (e.int("intensity") ?: Intensity.DEFAULT).coerceIn(Intensity.range))
        }.distinctBy { it.key }.take(3)
        val flat = text.replace(Regex("\\s"), "")
        val tags = listOf("people" to TagKind.PERSON, "places" to TagKind.PLACE, "activities" to TagKind.ACTIVITY).flatMap { (k, kind) ->
            o.strings(k).filter { it.isNotBlank() && it != "나" && it.replace(" ", "") in flat }.map { Tag(kind, it, auto = true) }
        }
        return emos.ifEmpty { return null } to tags
    }

    /** 말로 쓰기 정리. Falls back to the raw transcript. */
    suspend fun tidy(transcript: String): String = generateChecked(prompts.task("tidy"), transcript, transcript) ?: transcript
    suspend fun title(text: String): String? = generateChecked(prompts.task("title"), text, text)?.trim()
    /**
     * One-line summary saved with each entry; weekly/monthly retros only read these (ARCHITECTURE §5.3).
     * Gets the same sarcasm hint as the reply, else "기사님께 감사함" lands in the retro (REPORT_v2).
     */
    suspend fun summary(text: String): String? =
        generateChecked(prompts.task("summary"), text + (readingCache(text) ?: ""), text)

    /** [lines] like "11/10(월) 피곤: 새벽까지 회계원리 정리하고 시험 봄". [period] e.g. "일주일", "한 달". */
    suspend fun retro(lines: List<String>, period: String = "일주일"): String? {
        val t = prompts.task("retro")
        val joined = lines.joinToString("\n")
        return generateChecked(LlmPrompts.Task(t.cfg, t.system.replace("{period}", period)), joined, joined)
    }

    suspend fun question(recentLines: List<String>, frequentWords: List<String>): String? {
        val input = "최근 요약:\n" + recentLines.joinToString("\n") + "\n자주 나온 낱말: " + frequentWords.joinToString(", ")
        return generateChecked(prompts.task("question"), input, input)
    }

    /** 대화로 쓰기. [turns] = (온이 질문, 사용자 답) so far; returns 온이's next line (reaction + one question). */
    suspend fun chatAsk(turns: List<Pair<String, String>>): String? {
        val user = turns.joinToString("\n") { (q, a) -> "온이: $q\n나: $a" }
        return generateChecked(prompts.task("chat.ask"), user, turns.joinToString(" ") { it.second })
    }

    /** Compose the diary from the chat; only the user's words count as facts. */
    suspend fun chatCompose(turns: List<Pair<String, String>>): String? {
        val user = turns.joinToString("\n") { (q, a) -> "온이: $q\n나: $a" }
        return generateChecked(prompts.task("chat.compose"), user, turns.joinToString(" ") { it.second })
    }

    private suspend fun json(task: LlmPrompts.Task, user: String): JsonObject? = runCatching {
        val raw = generateOnce(task, user)
        Json.parseToJsonElement(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)).obj
    }.getOrNull()

    /** Generate, post-filter, retry once; null if both attempts fail (caller falls back). */
    private suspend fun generateChecked(task: LlmPrompts.Task, user: String, input: String): String? {
        repeat(2) {
            val out = runCatching { clean(generateOnce(task, user)) }
                .onFailure { android.util.Log.w("LlmReply", "generation failed: ${it.javaClass.simpleName}") } // no message: it may quote the prompt
                .getOrNull() ?: return@repeat
            val failed = filter.check(task.filters, out, input)
            if (out.isNotBlank() && failed.isEmpty()) return out
            android.util.Log.i("LlmReply", "filtered: $failed (${out.length} chars)") // reasons only: diary text never goes to logcat
        }
        return null
    }

    private suspend fun generateOnce(task: LlmPrompts.Task, user: String): String {
        var last = ""
        try {
            withTimeout(timeoutMs) {
                runtime.generate(LlmRequest(task.system, user, task.sampling, task.fewshot, task.schema)).collect { last = it }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("LLM timeout after ${timeoutMs}ms", e)
        }
        return last
    }

    companion object {
        const val FALLBACK = "오늘 이야기 들려줘서 고마워. 나 여기 불 켜 두고 있을게."
        private val TRAILERS = Regex("<end_of_turn>|<eos>|<\\|im_end\\|>")
        /** The chat prompt is a "온이: …/나: …" transcript, so the model sometimes echoes the speaker label: drop it. */
        private val SPEAKER = Regex("^(온이|Oni)\\s*[:：]\\s*")
        internal fun clean(s: String) = s.replace(TRAILERS, "").trim().removeSurrounding("\"").trim().replace(SPEAKER, "").trim()
    }
}
