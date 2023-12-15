package com.pass.data.repository

import com.pass.data.db.diary.DiaryDao
import com.pass.data.db.diary.DiaryDataBase
import com.pass.domain.model.Diary
import com.pass.data.repository.diary.DiaryRepositoryImpl
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
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
        val month = "10"
        val fakeDiaries = listOf(
            Diary(
                null,
                "2023",
                month,
                "24",
                "화",
                null,
                null,
                null,
                null,
                "",
                "일기 내용 테스트 10/24"
            ),
            Diary(
                null,
                "2023",
                month,
                "23",
                "월",
                null,
                null,
                null,
                null,
                "",
                "일기 내용 테스트 10/23"
            )
        )

        every { diaryDataBase.diaryDao().getDiariesByMonth("2023", month) } returns fakeDiaries

        val result = diaryRepository.getDiariesByMonth("2023", month)

        // 예상값과 기대값 비교 확인
        assertEquals(fakeDiaries, result)

        // 정확히 한 번만 호출되는지 확인
        verify(exactly = 1) { diaryDataBase.diaryDao().getDiariesByMonth("2023", month) }

        // 모든 예상된 동작이 검증되었는지 확인
        confirmVerified(diaryDataBase.diaryDao())
    }

    @Test
    fun testAddDiary(): Unit = runBlocking {
        val diary = Diary(
            null,
            "2023",
            "10",
            "30",
            "일",
            null,
            null,
            null,
            null,
            "",
            "일기 내용"
        )

        // addDiary 실행 시 실행만 되게 적용
        coEvery { diaryDataBase.diaryDao().addDiary(diary) } just Runs

        diaryRepository.addDiary(diary)

        // addDiary 가 정확히 한 번만 실행되었는지 확인
        coVerify(exactly = 1) { diaryDataBase.diaryDao().addDiary(diary) }
    }

    @Test
    fun testUpdateDiary(): Unit = runBlocking {
        val month = "11"
        val diary = Diary(
            null,
            "2023",
            month,
            "06",
            "월",
            null,
            null,
            null,
            null,
            "",
            "일기 내용 테스트 11/06"
        )

        // updateDiary 실행 시 실행만 되게 적용
        coEvery { diaryDataBase.diaryDao().updateDiary(diary) } just Runs

        diaryRepository.updateDiary(diary)

        // updateDiary 가 정확히 한 번만 실행되었는지 확인
        coVerify(exactly = 1) { diaryDataBase.diaryDao().updateDiary(diary) }
    }

    @Test
    fun testDeleteDiary(): Unit = runBlocking {
        val month = "11"
        val diary = Diary(
            null,
            "2023",
            month,
            "06",
            "월",
            null,
            null,
            null,
            null,
            "",
            "일기 내용 테스트 11/06"
        )

        // deleteDiary 실행 시 실행만 되게 적용
        coEvery { diaryDataBase.diaryDao().deleteDiary(diary) } just Runs

        diaryRepository.deleteDiary(diary)

        // deleteDiary 가 정확히 한 번만 실행되었는지 확인
        coVerify(exactly = 1) { diaryDataBase.diaryDao().deleteDiary(diary) }
    }
}