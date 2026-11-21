package com.pass.diary.feature.write

import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.ReplySource
import com.pass.diary.core.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * AI seams. The app binds core:ai behind them (app/.../AiBindings.kt). Everything runs on device; implementations
 * must never block the main thread.
 */

sealed interface ReplyResult {
    /** Oni's letter from the reply library. The UI types it out under the "온이의 한마디" label. */
    data class Letter(val text: String, val source: ReplySource, val templateId: String? = null) : ReplyResult

    /** Crisis wording detected: show the 109 / 1577-0199 card instead of a reply (always wins over a reply). */
    data object Crisis : ReplyResult

    /** 설정 › AI › 온이 답장 is off (crisis detection still ran and found nothing). */
    data object Off : ReplyResult
}

/** ARCHITECTURE §5.1 on save: exactly one of [ReplyResult.Crisis], [ReplyResult.Letter], [ReplyResult.Off]. */
interface ReplyProvider {
    /** [recentTemplateIds] = library replies used in the last 30 days (no repeats). */
    fun reply(entry: Entry, recentTemplateIds: Set<String>): Flow<ReplyResult>

    /** ① alone (fast, no model): run on the entry as it is now before showing ANY stored reply. */
    suspend fun isCrisis(entry: Entry): Boolean
}

data class ChatTurn(val fromOni: Boolean, val text: String)

/** "대화로 쓰기": Oni asks 3–5 questions, then turns the conversation into one diary entry. */
interface ChatResponder {
    /** Max questions Oni asks (the progress bar). */
    val maxQuestions: Int get() = 5

    /** ① on one user answer; checked on every turn before [next] / [compose]. A hit ends the chat with the help card. */
    suspend fun isCrisis(text: String): Boolean

    /** Oni's next message (first call: empty history). null = nothing more to ask. */
    suspend fun next(history: List<ChatTurn>): String?

    /** Quick replies shown under Oni's last question (may be empty). */
    fun suggestions(history: List<ChatTurn>): List<String> = emptyList()

    /** Turns the conversation into a diary body (the user's answers joined). */
    suspend fun compose(history: List<ChatTurn>): String
}

/** Editor hooks: suggestions only, never applied without a tap. */
interface WritingAssistant {
    /** Emotion suggestion from the body (classifier). Empty = no suggestion. */
    suspend fun suggestEmotions(body: String): List<EntryEmotion> = emptyList()

    /** People / places / activities found in the body, shown as dashed "+ 민지" chips. */
    suspend fun suggestTags(body: String): List<Tag> = emptyList()

    /** Auto title ("온이가 붙인 제목") while typing. null = none. */
    suspend fun suggestTitle(body: String): String? = autoTitle(body)

    /** The editor opened: start loading the classifier so the reply after saving doesn't wait for it. */
    fun warmUp() {}
}

/** First phrase of the body, ≤16 chars: the title an untitled entry gets on save. */
fun autoTitle(body: String): String? {
    val first = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("Q.") } ?: return null
    val sentence = first.split(Regex("(?<=[.!?])\\s")).first().trimEnd('.', '!', '?')
    if (sentence.length <= 16) return sentence
    return sentence.take(16).substringBeforeLast(' ').ifBlank { sentence.take(16) } + "…"
}
