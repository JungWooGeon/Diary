package com.pass.diary.feature.write

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.PhotoStore
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.EntrySource
import com.pass.diary.core.model.Photo
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import com.pass.diary.core.model.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * entryId = edit; otherwise a new entry on [date] (default today), optionally starting with a 1-tap [mood] key or today's
 * [question] (질문 템플릿). New entries start on the emotion picker, existing ones in the editor.
 */
@Serializable
data class WriteRoute(val entryId: String? = null, val date: String? = null, val mood: String? = null, val question: String? = null)

enum class WriteStep { PICK, EDIT }

data class WriteState(
    val entry: Entry? = null,
    val step: WriteStep = WriteStep.PICK,
    /** The emotion whose intensity the card below the picker controls (the last one tapped). */
    val focus: EmotionKey? = null,
    /** In the DB already (an empty new entry is only written once it has content). */
    val persisted: Boolean = false,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val importingPhotos: Boolean = false,
    val suggestedTags: List<Tag> = emptyList(),
    val suggestedTitle: String? = null,
    val suggestedEmotions: List<EntryEmotion> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
    private val photos: PhotoStore,
    private val assistant: WritingAssistant,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<WriteRoute>()
    private val _state = MutableStateFlow(WriteState())
    val state: StateFlow<WriteState> = _state.asStateFlow()
    private var pending: Job? = null
    private val saveLock = Mutex()

    init {
        assistant.warmUp()
        viewModelScope.launch {
            // A new entry's id is kept in the SavedStateHandle: after process death the same draft reopens (it was
            // autosaved under that id) instead of a blank editor plus an orphaned draft.
            val draftId = route.entryId ?: savedStateHandle[KEY_DRAFT]
                ?: java.util.UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT] = it }
            val existing = repository.get(draftId)
            val entry = existing ?: EntryRepository.newEntry(route.date ?: EntryRepository.today()).copy(id = draftId).let { e ->
                var n = e
                route.mood?.let(EmotionKey::of)?.let { n = n.copy(emotions = listOf(EntryEmotion(it))) }
                route.question?.let { n = n.copy(template = Template.QUESTION, body = "Q. $it\n") }
                n
            }
            val step = if (existing != null || route.question != null) WriteStep.EDIT else WriteStep.PICK
            _state.value = WriteState(entry, step = step, focus = entry.emotions.lastOrNull()?.key, persisted = existing != null)
            if (existing == null && entry.emotions.isNotEmpty()) scheduleSave(0)
        }
        // AI suggestions follow the body, 1.2 s after typing stops.
        viewModelScope.launch {
            _state.map { it.entry?.body }.filterNotNull().distinctUntilChanged().debounce(1_200).collect { body ->
                if (body.isBlank()) return@collect
                val tags = runCatching { assistant.suggestTags(body) }.getOrDefault(emptyList())
                val title = runCatching { assistant.suggestTitle(body) }.getOrNull()
                val emotions = runCatching { assistant.suggestEmotions(body) }.getOrDefault(emptyList())
                _state.update { s ->
                    val have = s.entry?.tags.orEmpty().map { it.value }.toSet()
                    val picked = s.entry?.emotions.orEmpty().map { it.key }.toSet()
                    s.copy(suggestedTags = tags.filter { it.value !in have }, suggestedTitle = title, suggestedEmotions = emotions.filter { it.key !in picked })
                }
            }
        }
    }

    private fun edit(block: (Entry) -> Entry) {
        val current = _state.value.entry ?: return
        val next = block(current)
        if (next == current) return
        _state.update { it.copy(entry = next) }
        scheduleSave(AUTOSAVE_MS)
    }

    /** Autosave 1 s after the last change (PLAN §3.1). */
    private fun scheduleSave(delayMs: Long) {
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(delayMs)
            saveNow()
        }
    }

    private suspend fun saveNow() = withContext(NonCancellable) {
        saveLock.withLock {
            val e = _state.value.entry ?: return@withLock
            if (!e.hasContent()) {
                // Typed then erased (or emotions un-picked): an already-written draft goes, nothing empty stays behind.
                if (_state.value.persisted) { repository.delete(e.id); _state.update { it.copy(persisted = false) } }
                return@withLock
            }
            _state.update { it.copy(saving = true) }
            repository.save(e)
            _state.update { it.copy(saving = false, persisted = true, savedAt = System.currentTimeMillis()) }
        }
    }

    /** Called before leaving the screen so nothing typed in the last second is lost. */
    fun flush(then: () -> Unit) {
        pending?.cancel()
        viewModelScope.launch { saveNow(); then() }
    }

    /** "저장": an untitled entry takes Oni's title, then on to the reply. */
    fun finish(then: (String) -> Unit) {
        pending?.cancel()
        val s = _state.value
        val e = s.entry ?: return
        if (e.title.isBlank() && !s.suggestedTitle.isNullOrBlank() && e.hasContent()) _state.update { it.copy(entry = e.copy(title = s.suggestedTitle)) }
        viewModelScope.launch { saveNow(); if (_state.value.persisted) then(e.id) }
    }

    fun goTo(step: WriteStep) = _state.update { it.copy(step = step) }

    fun toggleEmotion(key: EmotionKey) {
        val has = _state.value.entry?.emotions?.any { it.key == key } == true
        edit { e ->
            val next = when {
                has -> e.emotions.filterNot { it.key == key }
                key == EmotionKey.UNSURE -> listOf(EntryEmotion(key)) // "잘 모르겠음" stands alone
                else -> e.emotions.filterNot { it.key == EmotionKey.UNSURE } + EntryEmotion(key)
            }
            e.copy(emotions = next)
        }
        _state.update { s -> s.copy(focus = if (has) s.entry?.emotions?.lastOrNull { it.key != EmotionKey.UNSURE }?.key else key.takeIf { it != EmotionKey.UNSURE }) }
    }

    /** A tapped "온이가 느낀 감정" chip: added with the suggested intensity (suggestions are never applied on their own). */
    fun acceptEmotion(s: EntryEmotion) {
        edit { e -> e.copy(emotions = e.emotions.filterNot { it.key == EmotionKey.UNSURE || it.key == s.key } + s) }
        _state.update { st -> st.copy(focus = s.key, suggestedEmotions = st.suggestedEmotions.filterNot { it.key == s.key }) }
    }

    fun setIntensity(key: EmotionKey, intensity: Int) = edit { e ->
        e.copy(emotions = e.emotions.map { if (it.key == key) EntryEmotion(key, intensity.coerceIn(1, 5)) else it })
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }
    fun setBody(body: String) = edit { it.copy(body = body) }
    fun appendBody(text: String, source: EntrySource? = null) = edit { e ->
        e.copy(body = if (e.body.isBlank()) text else e.body.trimEnd() + "\n" + text, source = source ?: e.source)
    }
    fun setTemplate(t: Template) = edit { e ->
        e.copy(template = t, body = if (e.body.isBlank() || Template.entries.any { it.starter == e.body }) t.starter else e.body)
    }
    fun setWeather(w: String) = edit { it.copy(weather = w) }

    /**
     * 말로 쓰기: the raw transcript goes in at once; if the on-device LLM tidies it (numbers, spacing), the tidied text
     * replaces that raw piece, unless the user already edited it.
     */
    fun dictated(raw: String) {
        if (raw.isBlank()) return
        appendBody(raw, EntrySource.VOICE)
        viewModelScope.launch {
            val tidy = runCatching { assistant.tidy(raw) }.getOrDefault(raw)
            if (tidy.isNotBlank() && tidy != raw) edit { e -> if (raw in e.body) e.copy(body = e.body.replaceFirst(raw, tidy)) else e }
        }
    }

    fun setDate(date: String) = edit { it.copy(date = date) }
    fun setTime(time: String) = edit { it.copy(time = time, timeUnknown = false) }

    fun addTag(raw: String, kind: TagKind = TagKind.FREE, auto: Boolean = false) {
        edit { e ->
            val v = raw.trim().removePrefix("#").trim()
            if (v.isEmpty() || e.tags.any { it.value == v }) e else e.copy(tags = e.tags + Tag(kind, v, auto))
        }
        _state.update { s -> s.copy(suggestedTags = s.suggestedTags.filterNot { it.value == raw }) }
    }
    fun removeTag(tag: Tag) = edit { e -> e.copy(tags = e.tags - tag) }

    fun addPhotos(uris: List<Uri>) {
        val entry = _state.value.entry ?: return
        val room = PhotoStore.MAX_PER_ENTRY - entry.photos.size
        if (room <= 0 || uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importingPhotos = true) }
            val imported = uris.take(room).mapNotNull { photos.import(it, entry.id) }
            _state.update { it.copy(importingPhotos = false) }
            edit { e -> e.copy(photos = (e.photos + imported).take(PhotoStore.MAX_PER_ENTRY)) }
        }
    }

    fun removePhoto(photo: Photo) {
        edit { e -> e.copy(photos = e.photos - photo) }
        // ponytail: file goes right away; once sync exists it may want to keep it until the upload is reconciled.
        photos.delete(photo)
    }

    fun delete(then: () -> Unit) {
        pending?.cancel()
        val e = _state.value.entry ?: return then()
        viewModelScope.launch {
            withContext(NonCancellable) { if (_state.value.persisted) repository.delete(e.id) }
            then()
        }
    }

    override fun onCleared() {
        // Leaving without flush() (e.g. process going away with a pending debounce): save on a scope that outlives us.
        if (pending?.isActive == true) {
            pending?.cancel()
            val e = _state.value.entry
            if (e != null && e.hasContent()) outliving.launch { repository.save(e) }
            else if (e != null && _state.value.persisted) outliving.launch { repository.delete(e.id) }
        }
    }

    private companion object {
        const val AUTOSAVE_MS = 1_000L
        const val KEY_DRAFT = "draftEntryId"
        val outliving = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        /** A template's starter lines ("1. ", "Q. …") alone are not content. */
        fun Entry.hasContent() = emotions.isNotEmpty() || title.isNotBlank() || photos.isNotEmpty() || tags.isNotEmpty() ||
            body.lines().any { l -> l.isNotBlank() && l.trim() !in starterLines && !(template == Template.QUESTION && l.startsWith("Q. ")) }
        val starterLines = Template.entries.flatMap { it.starter.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
