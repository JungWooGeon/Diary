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

enum class SuggestionSource { CLASSIFIER }

/** Chips the user confirms; never apply silently. */
data class EmotionSuggestion(val emotions: List<EntryEmotion>, val tags: List<Tag>, val source: SuggestionSource)

/** Emotion-suggestion hook (e.g. while typing / before save): the on-device classifier. */
interface EmotionSuggester {
    suspend fun suggest(body: String): EmotionSuggestion
}

/** 대화로 쓰기: [turns] = (온이 question, user answer). Ask [OniChat.MIN_QUESTIONS]..[OniChat.MAX_QUESTIONS] times, then compose. */
interface ChatResponder {
    /** ① on one user answer. Callers check every answer before [next]/[compose]; a hit ends the chat with the help card. */
    fun isCrisis(answer: String): Boolean
    /** First question when [turns] is empty. null = an answer hit crisis detection: stop, show the help card. */
    suspend fun next(turns: List<Pair<String, String>>, group: EmotionGroup? = null, time: String? = null): String?
    /** The diary body: the user's answers joined. */
    suspend fun compose(turns: List<Pair<String, String>>): String
}

/** Library questions, then the user's own answers joined as the entry. */
class OniChat(
    private val template: TemplateReplyEngine,
    private val crisis: CrisisDetector,
) : ChatResponder {
    override fun isCrisis(answer: String) = crisis.detect(answer).crisis
    private fun anyCrisis(turns: List<Pair<String, String>>) = turns.any { isCrisis(it.second) }

    override suspend fun next(turns: List<Pair<String, String>>, group: EmotionGroup?, time: String?): String? {
        if (anyCrisis(turns)) return null // ARCHITECTURE §5.1 ①
        val asked = turns.map { it.first }.toSet()
        return generateSequence { template.question(group, emptyList(), time) }.take(10)
            .map { it?.text }.firstOrNull { it != null && it !in asked } ?: FIRST
    }

    override suspend fun compose(turns: List<Pair<String, String>>): String = turns.joinToString("\n") { it.second.trim() }

    companion object {
        const val MIN_QUESTIONS = 3
        const val MAX_QUESTIONS = 5
        const val FIRST = "오늘도 불 켜 두고 기다렸어. 하루 어땠어?"
    }
}
