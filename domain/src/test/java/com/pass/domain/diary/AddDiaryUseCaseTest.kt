package com.pass.domain.diary

import com.pass.domain.entity.Diary
import com.pass.domain.repository.diary.DiaryRepository
import com.pass.domain.usecase.diary.AddDiaryUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AddDiaryUseCaseTest {
    private val mockRepositoryImpl = mockk<DiaryRepository>()
    private val addDiaryUseCase = AddDiaryUseCase(mockRepositoryImpl)

    @Test
    fun testAddDiary() = runBlocking {
        val month = "10"
        val diary = Diary(
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
        )

        // addDiary 실행 시 실행만 되게 적용
        coEvery { mockRepositoryImpl.addDiary(diary) } just Runs

        addDiaryUseCase.invoke(diary)

        // addDiary 가 정확히 한 번만 실행되었는지 확인
        coVerify(exactly = 1) { mockRepositoryImpl.addDiary(diary) }
    }
}