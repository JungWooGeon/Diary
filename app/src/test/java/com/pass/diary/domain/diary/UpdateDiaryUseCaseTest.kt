package com.pass.diary.domain.diary

import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.diary.DiaryRepositoryImpl
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateDiaryUseCaseTest {
    private val mockRepositoryImpl = mockk<DiaryRepositoryImpl>()
    private val updateDiaryUseCase = UpdateDiaryUseCase(mockRepositoryImpl)

    @Test
    fun testUpdateDiary() = runBlocking {
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
            null,
            "",
            "일기 내용 테스트 11/06"
        )

        // addDiary 실행 시 실행만 되게 적용
        coEvery { mockRepositoryImpl.updateDiary(diary) } just Runs

        updateDiaryUseCase.invoke(diary)

        // addDiary 가 정확히 한 번만 실행되었는지 확인
        coVerify(exactly = 1) { mockRepositoryImpl.updateDiary(diary) }
    }
}