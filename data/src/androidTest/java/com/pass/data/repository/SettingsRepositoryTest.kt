package com.pass.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pass.data.repository.settings.SettingsRepositoryImpl
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var context: Context


    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepositoryImpl(context)
    }

    @Test
    fun testUpdateAndGetTextSize() = runBlocking {
        // 테스트할 텍스트 사이즈 값
        val testTextSize = 20f

        // 텍스트 사이즈 업데이트
        settingsRepository.updateCurrentFont(testTextSize.toString())

        // 업데이트된 텍스트 사이즈 값 가져오기
        val textSizeFlow = settingsRepository.getCurrentFont()

        // 가져온 값이 업데이트한 값과 동일한지 확인
        assertEquals(testTextSize.toString(), textSizeFlow.first())
    }
}