package com.pass.diary.data.repository

import com.pass.diary.data.db.diary.DiaryDao
import com.pass.diary.data.db.diary.DiaryDataBase
import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.diary.DiaryRepositoryImpl
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test


class DiaryRepositoryTest {

    private lateinit var diaryDataBase: DiaryDataBase
    private lateinit var diaryRepository: DiaryRepositoryImpl

    @Before
    fun setup() {
        val diaryDao = mockk<DiaryDao>()
        diaryDataBase = mockk<DiaryDataBase>().also {
            every { it.diaryDao() } returns diaryDao
        }

        diaryRepository = DiaryRepositoryImpl(diaryDataBase)
    }

    @Test
    fun testGetDiariesByMonth(): Unit = runBlocking {
        val fakeDiaries = listOf(
            Diary(
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
                "일기 내용 테스트 10/24"
            ),
            Diary(
                null,
                "2023",
                "10",
                "23",
                "월",
                null,
                null,
                null,
                null,
                null,
                "일기 내용 테스트 10/23"
            )
        )

        every { diaryDataBase.diaryDao().getDiariesByMonth("10") } returns fakeDiaries

        val result = diaryRepository.getDiariesByMonth("10")

        // 예상값과 기대값 비교 확인
        assertEquals(fakeDiaries, result)

        // 정확히 한 번만 호출되는지 확인
        verify(exactly = 1) { diaryDataBase.diaryDao().getDiariesByMonth("10") }

        // 모든 예상된 동작이 검증되었는지 확인
        confirmVerified(diaryDataBase.diaryDao())
    }
}