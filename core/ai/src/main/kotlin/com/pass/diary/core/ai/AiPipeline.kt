package com.pass.diary.core.ai

import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.ReplySource
import com.pass.diary.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface AiEvent {
    /** ① hit: show the help card (109 · 1577-0199). No reply, nothing else runs. */
    data class Crisis(val hits: List<String>) : AiEvent
    /**
     * ②③: suggestions for the user to confirm (never saved silently). [emotions] = what the reply used:
     * the user's own pick if any, else the suggestion.
     */
    data class Analysis(
        val emotions: List<EntryEmotion>,
        val suggestedEmotions: List<EntryEmotion>,
        val suggestedTags: List<Tag>,
        val source: SuggestionSource,
    ) : AiEvent
    data class Reply(val text: String, val source: ReplySource, val templateId: String?) : AiEvent
}

/**
 * ARCHITECTURE §5.1, run on every save. Entirely on-device.
 * [llm] returns the LLM engine only when a model is installed and the user turned "온이 깨우기" on.
 */
class AiPipeline(
    private val crisis: CrisisDetector,
    private val classifier: EmotionClassifier,
    private val tagExtractor: TagExtractor,
    private val template: TemplateReplyEngine,
    private val llm: () -> LlmReplyEngine?,
) : ReplyProvider, EmotionSuggester {
    /**
     * ② LLM emotion JSON when "온이 깨우기" is on (classifier group top-1 is only ~56% and weak on sarcasm),
     * classifier otherwise or when the LLM output is unusable. Tags: dictionary + LLM (only words found in the text).
     */
    override suspend fun suggest(body: String): EmotionSuggestion {
        val dictTags = tagExtractor.extract(body)
        llm()?.emotions(body)?.let { (emos, llmTags) ->
            return EmotionSuggestion(emos, (dictTags + llmTags).distinctBy { it.kind to it.value }, SuggestionSource.LLM)
        }
        return EmotionSuggestion(classifier.classify(body), dictTags, SuggestionSource.CLASSIFIER)
    }

    override fun onSave(
        body: String,
        userEmotions: List<EntryEmotion>,
        userTags: List<Tag>,
        recentTemplateIds: Set<String>,
    ): Flow<AiEvent> = flow {
        val c = crisis.detect(body)
        if (c.crisis) { emit(AiEvent.Crisis(c.hits)); return@flow }

        // The user's own pick wins anyway, so don't spend an LLM call (seconds on a phone) on suggestions they'll ignore.
        val s = if (userEmotions.isEmpty()) suggest(body)
            else EmotionSuggestion(classifier.classify(body), tagExtractor.extract(body), SuggestionSource.CLASSIFIER)
        val emotions = userEmotions.ifEmpty { s.emotions }
        val tags = (userTags + s.tags).distinctBy { it.kind to it.value }
        emit(AiEvent.Analysis(emotions, s.emotions, s.tags.filter { t -> userTags.none { it.kind == t.kind && it.value == t.value } }, s.source))

        val engine = llm()
        if (engine == null) {
            val t = template.reply(body, emotions, tags, recentTemplateIds)
            emit(AiEvent.Reply(t?.text ?: LlmReplyEngine.FALLBACK, ReplySource.TEMPLATE, t?.id))
            return@flow
        }
        engine.reply(body, emotions, tags, recentTemplateIds, userMood = userEmotions.firstOrNull()?.key).collect {
            emit(AiEvent.Reply(it.text, it.source, it.templateId))
        }
    }
}
