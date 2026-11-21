package com.pass.diary.feature.write

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.PhotoStore
import com.pass.diary.core.data.ReplyReports
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.data.db.ReplyEntity
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Template
import kotlinx.coroutines.flow.first
import com.pass.diary.core.model.ReplySource
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val CRISIS = "다 그만하고 싶다. 죽고 싶다"

private class FakeReplies : ReplyProvider {
    var generated = 0
    override fun reply(entry: Entry, recentTemplateIds: Set<String>): Flow<ReplyResult> = flow {
        generated++
        emit(if ("죽고 싶" in entry.body) ReplyResult.Crisis else ReplyResult.Letter("새 답장", ReplySource.TEMPLATE))
    }
    override suspend fun isCrisis(entry: Entry) = "죽고 싶" in entry.body
}

private class FakeChat : ChatResponder {
    val asked = mutableListOf<List<ChatTurn>>()
    var composed = 0
    override suspend fun isCrisis(text: String) = "죽고 싶" in text
    override suspend fun next(history: List<ChatTurn>): String? { asked += history; return "질문 ${history.count { it.fromOni } + 1}" }
    override suspend fun compose(history: List<ChatTurn>): String { composed++; return "정리된 일기" }
}

private object NoAssistant : WritingAssistant

/** Review #1, #2, #5 on a real (in-memory) Room DB. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WriteFlowTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private lateinit var db: DiaryDatabase
    private lateinit var repo: EntryRepository
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ctx, DiaryDatabase::class.java).build()
        repo = EntryRepository(db, db.entryDao())
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain(); main.close() }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 8_000
        while (!cond()) { check(System.currentTimeMillis() < end) { "timed out: $what" }; Thread.sleep(10) }
    }

    private fun entry(body: String) = runBlocking { repo.save(EntryRepository.newEntry().copy(body = body)) }
    private fun reply(id: String) = runBlocking { db.replyDao().upsert(ReplyEntity(id, "옛 답장", "template", null, 1, false)) }
    private fun replyVm(id: String, p: ReplyProvider) =
        ReplyViewModel(SavedStateHandle(mapOf("entryId" to id)), repo, db.replyDao(), p, NoAssistant, ReplyReports(ctx))

    // ---- #1 ----

    @Test fun storedReplyIsNotShownOverCrisisText() {
        val e = entry(CRISIS)
        reply(e.id) // e.g. written by an older version / another phone
        val vm = replyVm(e.id, FakeReplies())
        waitFor("crisis") { vm.state.value.ui == ReplyUi.Crisis }
        assertNull(runBlocking { db.replyDao().get(e.id) })
    }

    @Test fun editedBodyDropsReplyAndRegenerates() {
        val e = entry("카페에 갔다")
        reply(e.id)
        runBlocking { repo.save(e.copy(title = "제목만")) }
        assertNotNull("title-only edit keeps the reply", runBlocking { db.replyDao().get(e.id) })
        runBlocking { repo.save(e.copy(body = "카페에 갔다. 사실 너무 힘들었다")) }
        assertNull(runBlocking { db.replyDao().get(e.id) })

        val p = FakeReplies()
        val vm = replyVm(e.id, p)
        waitFor("reply") { vm.state.value.ui is ReplyUi.Arrived }
        assertEquals("새 답장", (vm.state.value.ui as ReplyUi.Arrived).text)
        assertEquals(1, p.generated)
    }

    @Test fun crisisAddedInEditShowsCardNotOldReply() {
        val e = entry("카페에 갔다")
        reply(e.id)
        runBlocking { repo.save(e.copy(body = "카페에 갔다. $CRISIS")) }
        val vm = replyVm(e.id, FakeReplies())
        waitFor("crisis") { vm.state.value.ui == ReplyUi.Crisis }
    }

    // ---- #2 ----

    @Test fun chatCrisisAnswerStopsBeforeNextQuestion() {
        val chat = FakeChat()
        val vm = ChatViewModel(SavedStateHandle(), repo, db.chatMessageDao(), chat)
        waitFor("first question") { vm.state.value.turns.size == 1 }
        assertTrue(vm.send(CRISIS))
        waitFor("crisis") { vm.state.value.crisis }
        assertEquals(1, chat.asked.size) // only the opening question; the crisis answer never went to next()
        vm.compose { error("no compose after crisis") }
        assertTrue(!vm.send("다음 얘기"))
        Thread.sleep(200)
        assertEquals(0, chat.composed)
    }

    // ---- #5 ----

    @Test fun chatTurnsArePersistedAtOnceAndSurviveProcessDeath() {
        val handle = SavedStateHandle()
        val vm = ChatViewModel(handle, repo, db.chatMessageDao(), FakeChat())
        waitFor("first question") { vm.state.value.turns.size == 1 }
        vm.send("카페에 갔어")
        waitFor("second question") { vm.state.value.turns.size == 3 }
        val draft = handle.get<String>("chatDraftId")!!
        assertEquals(listOf("질문 1", "카페에 갔어", "질문 2"), runBlocking { db.chatMessageDao().get(draft) }.map { it.text })
        assertEquals("카페에 갔어", runBlocking { repo.get(draft) }!!.body)

        // process death: a new ViewModel with the restored SavedStateHandle continues the same draft
        val again = ChatViewModel(handle, repo, db.chatMessageDao(), FakeChat())
        waitFor("restored") { again.state.value.turns.size == 3 }
        assertEquals(vm.state.value.turns, again.state.value.turns)

        var composedId: String? = null
        again.compose { composedId = it }
        waitFor("composed") { composedId != null }
        assertEquals(draft, composedId)
        assertEquals("정리된 일기", runBlocking { repo.get(draft) }!!.body) // raw answers replaced, not duplicated
    }

    @Test fun newEntryEditorReopensSameDraftAfterProcessDeath() {
        val handle = SavedStateHandle()
        val vm = WriteViewModel(handle, repo, PhotoStore(ctx), NoAssistant)
        waitFor("entry") { vm.state.value.entry != null }
        vm.goTo(WriteStep.EDIT)
        vm.setBody("새벽에 쓴 일기")
        var flushed = false
        vm.flush { flushed = true }
        waitFor("flushed") { flushed }

        val again = WriteViewModel(handle, repo, PhotoStore(ctx), NoAssistant)
        waitFor("entry") { again.state.value.entry != null }
        assertEquals(vm.state.value.entry!!.id, again.state.value.entry!!.id)
        assertEquals("새벽에 쓴 일기", again.state.value.entry!!.body)
        assertTrue(again.state.value.persisted)
    }

    // ---- blank drafts never stay ----
    private fun newVm(): WriteViewModel = WriteViewModel(SavedStateHandle(), repo, PhotoStore(ctx), NoAssistant).also { vm ->
        waitFor("entry") { vm.state.value.entry != null }
    }
    private fun WriteViewModel.flushNow() { var done = false; flush { done = true }; waitFor("flushed") { done } }
    private fun stored(vm: WriteViewModel) = runBlocking { repo.get(vm.state.value.entry!!.id) }

    @Test fun typedThenErasedLeavesNoEntry() {
        val vm = newVm()
        vm.setBody("hello")
        waitFor("autosaved") { vm.state.value.persisted }
        vm.setBody("")
        vm.flushNow()
        assertTrue(stored(vm)?.deleted ?: true)
        assertTrue(runBlocking { repo.observeDay(EntryRepository.today()).first() }.isEmpty())
    }

    @Test fun unpickedMoodLeavesNoEntry() {
        val vm = newVm()
        vm.toggleEmotion(EmotionKey.JOY)
        vm.flushNow()
        assertTrue(vm.state.value.persisted)
        vm.toggleEmotion(EmotionKey.JOY)
        vm.flushNow()
        assertTrue(runBlocking { repo.observeDay(EntryRepository.today()).first() }.isEmpty())
    }

    @Test fun templateStarterAloneIsNotSaved() {
        val vm = newVm()
        vm.setTemplate(Template.THREE_LINES)
        vm.flushNow()
        assertNull(stored(vm))
    }

    @Test fun draftWithContentIsKept() {
        val vm = newVm()
        vm.setTemplate(Template.THREE_LINES)
        vm.setBody("1. hello\n2. \n3. ")
        vm.flushNow()
        assertEquals("1. hello\n2. \n3. ", stored(vm)!!.body)
    }
}
