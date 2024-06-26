package com.pass.domain.diary

import com.pass.domain.entity.Diary
import com.pass.domain.repository.diary.DiaryRepository
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GetDiariesByMonthUseCaseTest {

    private val mockRepositoryImpl = mockk<DiaryRepository>()
    private val getDiariesByMonthUseCase = GetDiariesByMonthUseCase(mockRepositoryImpl)

    @Test
    fun testGetDiariesByMonth() = runBlocking {
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

        coEvery { mockRepositoryImpl.getDiariesByMonth("2023", month) } returns fakeDiaries

        val result = getDiariesByMonthUseCase("2023", month)

        // 예상값과 기대값 비교 확인
        assertEquals(fakeDiaries, result)

        // 정확히 한 번만 호출되는지 확인
        coVerify(exactly = 1) { mockRepositoryImpl.getDiariesByMonth("2023", month) }

        // 모든 예상된 동작이 검증되었는지 확인
        confirmVerified(mockRepositoryImpl)
    }
}
