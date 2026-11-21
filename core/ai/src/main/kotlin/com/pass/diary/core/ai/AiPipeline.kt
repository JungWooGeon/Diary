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

/** ARCHITECTURE §5.1, run on every save. Entirely on-device: crisis rules, classifier, tag dictionary, library reply. */
class AiPipeline(
    private val crisis: CrisisDetector,
    private val classifier: EmotionClassifier,
    private val tagExtractor: TagExtractor,
    private val template: TemplateReplyEngine,
) : ReplyProvider, EmotionSuggester {
    override suspend fun suggest(body: String): EmotionSuggestion =
        EmotionSuggestion(classifier.classify(body), tagExtractor.extract(body), SuggestionSource.CLASSIFIER)

    override fun onSave(
        body: String,
        userEmotions: List<EntryEmotion>,
        userTags: List<Tag>,
        recentTemplateIds: Set<String>,
    ): Flow<AiEvent> = flow {
        val c = crisis.detect(body)
        if (c.crisis) { emit(AiEvent.Crisis(c.hits)); return@flow }

        val s = suggest(body)
        val emotions = userEmotions.ifEmpty { s.emotions }
        val tags = (userTags + s.tags).distinctBy { it.kind to it.value }
        emit(AiEvent.Analysis(emotions, s.emotions, s.tags.filter { t -> userTags.none { it.kind == t.kind && it.value == t.value } }, s.source))

        val t = template.reply(body, emotions, tags, recentTemplateIds)
        emit(AiEvent.Reply(t?.text ?: FALLBACK, ReplySource.TEMPLATE, t?.id))
    }

    companion object {
        /** When the library has nothing that fits. */
        const val FALLBACK = "오늘 이야기 들려줘서 고마워. 나 여기 불 켜 두고 있을게."
    }
}
