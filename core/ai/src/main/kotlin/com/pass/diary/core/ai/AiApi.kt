package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Tag
import kotlinx.coroutines.flow.Flow


/** Entry points for the feature modules (workstream F). All on-device; bound in [AiModule]. */

/** Save -> crisis / suggestions / Oni's reply (ARCHITECTURE §5.1). Implemented by [AiPipeline]. */
interface ReplyProvider {
    fun onSave(
        body: String,
        userEmotions: List<EntryEmotion> = emptyList(),
        userTags: List<Tag> = emptyList(),
        recentTemplateIds: Set<String> = emptySet(),
    ): Flow<AiEvent>
}

enum class SuggestionSource { LLM, CLASSIFIER }

/** Chips the user confirms; never apply silently. */
data class EmotionSuggestion(val emotions: List<EntryEmotion>, val tags: List<Tag>, val source: SuggestionSource)

/** Emotion-suggestion hook (e.g. while typing / before save). LLM JSON when "온이 깨우기" is on, else the classifier. */
interface EmotionSuggester {
    suspend fun suggest(body: String): EmotionSuggestion
}

/** 대화로 쓰기: [turns] = (온이 question, user answer). Ask [OniChat.MIN_QUESTIONS]..[OniChat.MAX_QUESTIONS] times, then compose. */
interface ChatResponder {
    /** ① on one user answer. Callers check every answer before [next]/[compose]; a hit ends the chat with the help card. */
    fun isCrisis(answer: String): Boolean
    /** First question when [turns] is empty. null = an answer hit crisis detection: stop, show the help card. */
    suspend fun next(turns: List<Pair<String, String>>, group: EmotionGroup? = null, time: String? = null): String?
    /** The diary body made from the user's answers (never through the LLM when an answer hit crisis detection). */
    suspend fun compose(turns: List<Pair<String, String>>): String
}

data class ModelState(
    val specs: List<ModelSpec>,
    /** Models this phone can run (menu hidden when empty). */
    val supported: List<ModelSpec>,
    val installed: ModelSpec?,
    val enabled: Boolean,
    val wifiRecommended: Boolean,
)

/** 마이 → 온이 깨우기. */
interface ModelStateProvider {
    fun state(): ModelState
    fun gate(spec: ModelSpec): ModelManager.Gate
    fun download(spec: ModelSpec): Flow<DownloadState>
    fun delete(spec: ModelSpec)
    fun setEnabled(on: Boolean)
}


/** Chat fallback without an LLM: library questions, then the user's own answers joined as the entry. */
class OniChat(
    private val llm: () -> LlmReplyEngine?,
    private val template: TemplateReplyEngine,
    private val crisis: CrisisDetector,
) : ChatResponder {
    override fun isCrisis(answer: String) = crisis.detect(answer).crisis
    private fun anyCrisis(turns: List<Pair<String, String>>) = turns.any { isCrisis(it.second) }

    override suspend fun next(turns: List<Pair<String, String>>, group: EmotionGroup?, time: String?): String? {
        if (anyCrisis(turns)) return null // before any LLM call (ARCHITECTURE §5.1 ①)
        if (turns.isNotEmpty()) llm()?.chatAsk(turns)?.let { return it }
        val asked = turns.map { it.first }.toSet()
        return generateSequence { template.question(group, emptyList(), time) }.take(10)
            .map { it?.text }.firstOrNull { it != null && it !in asked } ?: FIRST
    }

    override suspend fun compose(turns: List<Pair<String, String>>): String =
        (if (anyCrisis(turns)) null else llm()?.chatCompose(turns)) ?: turns.joinToString("\n") { it.second.trim() }

    companion object {
        const val MIN_QUESTIONS = 3
        const val MAX_QUESTIONS = 5
        const val FIRST = "오늘도 불 켜 두고 기다렸어. 하루 어땠어?"
    }
}
