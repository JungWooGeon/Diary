package com.pass.diary.feature.today

import androidx.room.Room
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.model.EmotionKey
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 1탭 기분 → 되돌리기 removes exactly the entry its snackbar announced. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuickUndoTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private lateinit var db: DiaryDatabase
    private lateinit var vm: TodayViewModel

    @Before fun setUp() {
        Dispatchers.setMain(main)
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, DiaryDatabase::class.java).build()
        vm = TodayViewModel(ctx, EntryRepository(db, db.entryDao()), db.replyDao())
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain(); main.close() }

    private fun today() = runBlocking { EntryRepository(db, db.entryDao()).observeDay(EntryRepository.today()).first() }
        .map { it.emotions.single().key }

    @Test fun undoRightAfterTapRemovesThatEntry() {
        val saved = vm.quickMood(EmotionKey.JOY)
        val t = System.currentTimeMillis()
        runBlocking { vm.undoQuick(saved).join() } // before the save has necessarily returned
        assertEquals(emptyList<EmotionKey>(), today())
        assertTrue("undo waited for Oni's 3 s cheer", System.currentTimeMillis() - t < 2_000)
    }

    @Test fun undoOfEarlierMoodKeepsTheLaterOne() {
        val first = vm.quickMood(EmotionKey.SAD)
        val second = vm.quickMood(EmotionKey.TIRED)
        runBlocking { second.await(); vm.undoQuick(second).join() }
        assertEquals(listOf(EmotionKey.SAD), today())
        runBlocking { vm.undoQuick(first).join() }
        assertEquals(emptyList<EmotionKey>(), today())
    }
}
