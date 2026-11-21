package com.pass.diary.feature.today

import androidx.room.Room
import com.pass.diary.core.billing.NudgeGate
import com.pass.diary.core.data.EntryRepository
import com.pass.diary.core.data.SettingsRepository
import com.pass.diary.core.data.db.DiaryDatabase
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EntryEmotion
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A crisis-worded entry yesterday, no heavy run: still nothing is sold on 오늘 (the 7일 연속 nudge did show). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GrowthCrisisTest {
    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private lateinit var db: DiaryDatabase

    @Before fun setUp() { Dispatchers.setMain(main); db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryDatabase::class.java).build() }
    @After fun tearDown() { db.close(); Dispatchers.resetMain(); main.close() }

    @Test fun crisisWithoutHeavyRunBlocksTheStreakNudge() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = EntryRepository(db, db.entryDao())
        val today = LocalDate.now()
        for (d in 0..7L) {
            val crisis = d == 1L
            repo.save(EntryRepository.newEntry(today.minusDays(d).toString()).copy(
                body = if (crisis) "SOS" else "산책했다",
                emotions = listOf(EntryEmotion(if (crisis) EmotionKey.SAD else EmotionKey.JOY, 3)),
            ))
        }
        val vm = GrowthViewModel(ctx, repo, SettingsRepository(ctx), object : TodayAi {
            override suspend fun isCrisis(text: String) = "SOS" in text
        }, NudgeGate(ctx))
        val g = withTimeout(10_000) { vm.growth.filterNotNull().first() }
        assertTrue(g.crisis)
        assertTrue(g.streak >= 7)
        assertNull(nudgeFor(g, premium = false, gate = vm.nudges))
    }
}
