package com.pass.diary

import android.util.Log
import com.pass.diary.core.ai.AiEvent
import com.pass.diary.core.ai.AiPipeline
import com.pass.diary.core.ai.CrisisDetector
import com.pass.diary.core.ai.EmotionClassifier
import com.pass.diary.core.ai.TagExtractor
import com.pass.diary.core.ai.TemplateReplyEngine
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.ReplySource
import com.pass.diary.core.model.Tag
import com.pass.diary.feature.write.ChatResponder
import com.pass.diary.feature.write.ChatTurn
import com.pass.diary.feature.write.ReplyProvider
import com.pass.diary.feature.write.ReplyResult
import com.pass.diary.feature.write.WritingAssistant
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pass.diary.core.ai.ChatResponder as CoreChat

/*
 * core:ai behind the feature seams (feature/write AiHooks.kt). core:ai objects are taken as
 * dagger.Lazy: building them reads the 16 MB classifier, which must not happen on the main thread when a screen opens.
 */

// ---------------------------------------------------------------------------------------------------- 답장 (§5.1)

class AiReplyProvider @Inject constructor(
    private val crisis: Lazy<CrisisDetector>,
    private val classifier: Lazy<EmotionClassifier>,
    private val tags: Lazy<TagExtractor>,
    private val template: Lazy<TemplateReplyEngine>,
    private val settings: SettingsRepository,
) : ReplyProvider {
    override fun reply(entry: Entry, recentTemplateIds: Set<String>): Flow<ReplyResult> = flow {
        // ① before anything heavy loads (the classifier takes seconds on a cold start): a crisis card must never wait.
        // It also runs with every AI switch off: it is a safety rule (설정 › AI says so).
        if (crisis.get().detect(entry.aiText()).crisis) { emit(ReplyResult.Crisis); return@flow }
        val s = settings.settings.first()
        if (!s.aiReply) { emit(ReplyResult.Off); return@flow } // no classifier
        AiPipeline(crisis.get(), classifier.get(), tags.get(), template.get())
            .onSave(entry.aiText(), entry.emotions, entry.tags, recentTemplateIds)
            .catch { // class only, never the message: it may quote the diary
                Log.w("AiReply", "reply failed, using fallback: ${it.javaClass.simpleName}")
                emit(AiEvent.Reply(AiPipeline.FALLBACK, ReplySource.TEMPLATE, null))
            }
            .collect { e -> e.toUi()?.let { emit(it) } }
    }.flowOn(Dispatchers.Default)

    override suspend fun isCrisis(entry: Entry) = withContext(Dispatchers.Default) { crisis.get().detect(entry.aiText()).crisis }
}

/** What the AI reads: the title only when the user wrote one, then the body. */
internal fun Entry.aiText() = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")

internal fun AiEvent.toUi(): ReplyResult? = when (this) {
    is AiEvent.Crisis -> ReplyResult.Crisis
    is AiEvent.Analysis -> null // the editor already offered these as chips; the reply screen doesn't re-ask
    is AiEvent.Reply -> ReplyResult.Letter(text, source, templateId)
}

// ---------------------------------------------------------------------------------------------------- 편집기 제안

class AiWritingAssistant @Inject constructor(
    private val classifier: Lazy<EmotionClassifier>,
    private val tags: Lazy<TagExtractor>,
    private val settings: SettingsRepository,
    private val embeddings: EmbeddingStore,
) : WritingAssistant {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun suggestEmotions(body: String): List<EntryEmotion> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiSuggest) emptyList() else classifier.get().classify(body).filter { it.key != EmotionKey.UNSURE }
    }

    override suspend fun suggestTags(body: String): List<Tag> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiSuggest) emptyList() else tags.get().extract(body, max = 5)
    }

    override suspend fun suggestTitles(body: String, tags: List<Tag>): List<String> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiSuggest) emptyList()
        else com.pass.diary.core.ai.Insights.titles(body, tags, embeddings::embedSentence.takeIf { embeddings.canEmbed })
    }

    override fun warmUp() {
        scope.launch {
            val s = settings.settings.first()
            if (!s.aiSuggest && !s.aiReply) return@launch
            classifier.get() // LiteRT interpreter + tokenizer: seconds on a cold start, so not when the reply screen opens
        }
    }
}

// ---------------------------------------------------------------------------------------------------- 대화로 쓰기

class AiChatResponder @Inject constructor(private val chat: Lazy<CoreChat>) : ChatResponder {
    override val maxQuestions = com.pass.diary.core.ai.OniChat.MAX_QUESTIONS

    override suspend fun isCrisis(text: String) = withContext(Dispatchers.Default) { chat.get().isCrisis(text) }

    override suspend fun next(history: List<ChatTurn>): String? = withContext(Dispatchers.Default) {
        if (history.count { it.fromOni } >= maxQuestions) null else chat.get().next(history.toPairs(), time = timeOfDay())
    }

    override fun suggestions(history: List<ChatTurn>) = listOf("다음 질문")

    override suspend fun compose(history: List<ChatTurn>): String = withContext(Dispatchers.Default) { chat.get().compose(history.toPairs()) }

    private fun timeOfDay() = LocalTime.now().hour.let { if (it in 5..10) "morning" else if (it in 11..17) "day" else "night" }
}

/** UI turns -> core (온이 question, user answer) pairs. A skipped question (no answer) is dropped. */
internal fun List<ChatTurn>.toPairs(): List<Pair<String, String>> = buildList {
    var q: String? = null
    for (t in this@toPairs) if (t.fromOni) q = t.text else { add((q ?: "") to t.text); q = null }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingModule {
    @Binds abstract fun reply(impl: AiReplyProvider): ReplyProvider
    @Binds abstract fun chat(impl: AiChatResponder): ChatResponder
    @Binds abstract fun assistant(impl: AiWritingAssistant): WritingAssistant
}
