package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepositoryImpl
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UpdateCurrentTextSizeUseCaseTest {

    private val mockRepositoryImpl = mockk<SettingsRepositoryImpl>(relaxed = true)
    private val updateCurrentTextSizeUseCase = UpdateCurrentTextSizeUseCase(mockRepositoryImpl)

    @Test
    fun testUpdateCurrentTextSize() = runBlocking {
        // 테스트 사이즈 값
        val testTextSize = 20f

        // updateCurrentFont 함수가 호출 되었는지 확인
        coEvery { mockRepositoryImpl.updateCurrentTextSize(any()) } just Runs

        // UpdateCurrentTextSizeUseCase 실행
        updateCurrentTextSizeUseCase(testTextSize)

        // updateCurrentFont 함수가 올바른 인자로 호출 되었는지 확인
        coVerify { mockRepositoryImpl.updateCurrentTextSize(testTextSize) }
    }
}
