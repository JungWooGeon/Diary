package com.pass.diary

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.pass.diary.core.ai.CrisisDetector
import com.pass.diary.core.ai.EmbeddingIndex
import com.pass.diary.core.ai.EmotionClassifier
import com.pass.diary.core.ai.Insights
import com.pass.diary.core.ai.SentenceEmbedder
import com.pass.diary.core.ai.classifySentences
import com.pass.diary.core.data.DiaryInsights
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.data.db.EntryDao
import com.pass.diary.core.data.db.EntryText
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.SentenceMark
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sentence index behind 비슷했던 날 / 뜻으로 찾기 (AI_GROWTH §2.1 E4, E5): entry id -> embedding of its text, kept in
 * filesDir/ai/embeddings.bin. Derived data — not synced, rebuilt when missing or [EmbeddingIndex.VERSION] changes.
 *
 * It follows the live entries in Room ([EntryDao.observeTexts]) instead of hooking every write: a save, a Drive download,
 * 휴지통 / 되살리기, purge and v1 migration all end up there. Each vector carries its text's hash, so only changed entries are
 * embedded again. 계정·데이터 삭제 wipes filesDir with the file in it (AppDataWiper).
 */
@Singleton
class EmbeddingStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: EntryDao,
    private val repository: EntryRepository,
    private val settings: SettingsRepository,
    private val classifier: Lazy<EmotionClassifier>,
    private val crisis: Lazy<CrisisDetector>,
) : DiaryInsights {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val file get() = File(context.filesDir, "ai/embeddings.bin")
    /** null until [start] has read the file; screens opened right after launch wait for it ([loadedIndex]). */
    private val index = kotlinx.coroutines.flow.MutableStateFlow<EmbeddingIndex?>(null)
    private suspend fun loadedIndex() = withTimeoutOrNull(LOAD_WAIT_MS) { index.filterNotNull().first() }
    @Volatile private var texts: Map<String, EntryText> = emptyMap()
    private val embedder by lazy { SentenceEmbedder.of(classifier.get()) }
    private val marks = LruCache<Int, List<SentenceMark>>(48)

    /**
     * App start (after migration): the file is read at once, embedding waits a little so the first screen isn't competing
     * with the 16 MB model load. Saves while typing settle for [SETTLE_MS] before the draft is embedded again.
     */
    @OptIn(FlowPreview::class)
    fun start() = scope.launch {
        index.value = runCatching { file.inputStream().use(EmbeddingIndex::read) }.getOrNull() ?: EmbeddingIndex()
        delay(START_DELAY_MS)
        combine(dao.observeTexts(), settings.settings) { t, s -> t.takeIf { s.aiRecall } }.debounce(SETTLE_MS).collect { live ->
            texts = live.orEmpty().associateBy { it.id }
            if (live == null) return@collect
            runCatching { sync(live) }.onFailure { Log.w(TAG, "index sync failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun sync(live: List<EntryText>) {
        if (!embedder.available) return
        val idx = index.value ?: return
        val want = live.mapNotNull { t -> text(t)?.let { t.id to it } }.toMap()
        var changed = false
        (idx.ids() - want.keys).forEach { idx.remove(it); changed = true }
        val t0 = System.currentTimeMillis(); var n = 0
        for ((id, text) in want) {
            if (idx.stamp(id) == text.hashCode() && idx[id] != null) continue
            embedder.embed(text)?.let { idx.put(id, it, text.hashCode()); changed = true; n++ }
        }
        if (n > 0) Log.d(TAG, "embedded $n entries in ${System.currentTimeMillis() - t0} ms (index ${idx.size})")
        if (changed) save(idx)
    }

    private fun save(idx: EmbeddingIndex) = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use(idx::write)
        tmp.renameTo(file)
    }

    /** What is embedded: the title only when the user wrote one, then the body (as the reply reads it). */
    private fun text(t: EntryText) = listOf(t.title, t.body).filter { it.isNotBlank() }.joinToString("\n").trim()
        .takeIf { it.length >= Insights.MIN_INDEX_CHARS }

    private fun isCrisis(text: String) = crisis.get().detect(text).crisis

    override suspend fun similarDays(entryId: String, max: Int): List<Entry> = withContext(Dispatchers.Default) {
        if (!settings.settings.first().aiRecall || !embedder.available) return@withContext emptyList()
        val entry = repository.get(entryId)?.takeIf { !it.deleted } ?: return@withContext emptyList()
        val text = text(EntryText(entry.id, entry.date, entry.title, entry.body)) ?: return@withContext emptyList()
        if (isCrisis(text)) return@withContext emptyList()
        val idx = loadedIndex() ?: return@withContext emptyList()
        // Just saved: the index may still be a debounce behind.
        val v = idx[entryId]?.takeIf { idx.stamp(entryId) == text.hashCode() } ?: embedder.embed(text) ?: return@withContext emptyList()
        idx.topK(v, max * 4, exclude = setOf(entryId))
            .filter { it.second >= Insights.SIMILAR_MIN_COS }
            .mapNotNull { (id, _) -> repository.get(id) }
            .filter { !it.deleted && it.date < entry.date && !isCrisis(it.title + "\n" + it.body) }
            .take(max)
    }

    override suspend fun sentenceMarks(body: String): List<SentenceMark> = withContext(Dispatchers.Default) {
        if (body.isBlank()) return@withContext emptyList()
        marks.get(body.hashCode())?.let { return@withContext it }
        val c = classifier.get()
        if (!c.usesModel) return@withContext emptyList()
        Insights.marks(body, c.classifySentences(body)).also { marks.put(body.hashCode(), it) }
    }

    override suspend fun searchByMeaning(query: String, max: Int): List<String> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.length < 2 || !settings.settings.first().aiRecall || !embedder.available) return@withContext emptyList()
        val idx = loadedIndex() ?: return@withContext emptyList()
        val v = embedder.embed(q) ?: return@withContext emptyList()
        val byId = texts
        idx.topK(v, max) { id, cos -> EmbeddingIndex.searchScore(cos, EmbeddingIndex.bigramOverlap(q, byId[id]?.let(::text).orEmpty())) }
            .filter { it.second >= Insights.SEARCH_MIN_SCORE }.map { it.first }
    }

    /** E6: sentence vectors are cached by text, so a pause in typing only embeds the sentences that changed. */
    private val sentenceVectors = LruCache<String, FloatArray>(96)
    fun embedSentence(s: String): FloatArray? = sentenceVectors.get(s) ?: embedder.embed(s)?.also { sentenceVectors.put(s, it) }
    val canEmbed get() = embedder.available

    private companion object {
        const val TAG = "EmbeddingStore"
        const val START_DELAY_MS = 4_000L
        const val SETTLE_MS = 3_000L
        const val LOAD_WAIT_MS = 5_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InsightsModule {
    @Binds abstract fun insights(impl: EmbeddingStore): DiaryInsights
}
