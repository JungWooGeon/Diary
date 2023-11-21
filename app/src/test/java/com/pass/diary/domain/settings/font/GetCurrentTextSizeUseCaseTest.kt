package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepositoryImpl
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GetCurrentTextSizeUseCaseTest {

    private val mockRepositoryImpl = mockk<SettingsRepositoryImpl>()
    private val getCurrentTextSizeUseCase = GetCurrentTextSizeUseCase(mockRepositoryImpl)

    @Test
    fun testGetCurrentFont() = runBlocking {
        // 테스트 사이즈 값
        val testTextSize = 20f
        val testTextSizeFlow = flowOf(testTextSize)

        // getCurrentFont 함수의 동작을 모의
        coEvery { mockRepositoryImpl.getCurrentTextSize() } returns testTextSizeFlow

        // GetCurrentFontUseCase 실행
        val textSizeFlow = getCurrentTextSizeUseCase()

        // 반환된 Flow 가 모의한 Flow 와 동일한 지 확인
        assertEquals(testTextSizeFlow.first(), textSizeFlow.first())
    }
}