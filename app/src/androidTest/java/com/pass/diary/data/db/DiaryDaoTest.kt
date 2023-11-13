package com.pass.diary.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pass.diary.data.db.diary.DiaryDao
import com.pass.diary.data.db.diary.DiaryDataBase
import com.pass.diary.data.entity.Diary
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiaryDaoTest {
    private lateinit var db: DiaryDataBase
    private lateinit var dao: DiaryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiaryDataBase::class.java).build()
        dao = db.diaryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testGetDiariesWhenNoDiaryInserted(): Unit = runBlocking {
        val diaries = dao.getDiariesByMonth("2023", "10")
        assertTrue(diaries.isEmpty())
    }

    @Test
    fun testGetDiariesWhenDiaryInserted(): Unit = runBlocking {
        val newDiary = Diary(
            null,
            "2023",
            "10",
            "24",
            "화",
            null,
            null,
            null,
            null,
            null,
            "일기 내용 테스트"
        )

        dao.addDiary(newDiary)

        val diaries = dao.getDiariesByMonth("2023", "10")
        assertTrue(diaries.isNotEmpty())
        assertEquals("2023", diaries[0].year)
    }
}
