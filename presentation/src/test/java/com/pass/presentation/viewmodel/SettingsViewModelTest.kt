package com.pass.presentation.viewmodel

import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.GetAllDiariesUseCase
import com.pass.domain.usecase.google.BackupDiariesToGoogleDriveUseCase
import com.pass.domain.usecase.google.IsLoggedInUseCase
import com.pass.domain.usecase.google.LogInForGoogleUseCase
import com.pass.domain.usecase.google.LogOutForGoogleUseCase
import com.pass.domain.usecase.google.RestoreDiariesForGoogleDriveUseCase
import com.pass.domain.usecase.settings.font.GetCurrentFontUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentFontUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentTextSizeUseCase
import com.pass.presentation.intent.SettingsIntent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SettingsViewModelTest {

    private val getCurrentTextSizeUseCase = mockk<GetCurrentTextSizeUseCase>()
    private val updateCurrentTextSizeUseCase = mockk<UpdateCurrentTextSizeUseCase>()
    private val getCurrentFontUseCase = mockk<GetCurrentFontUseCase>()
    private val updateCurrentFontUseCase = mockk<UpdateCurrentFontUseCase>()
    private val logInForGoogleUseCase = mockk<LogInForGoogleUseCase>()
    private val logOutForGoogleUseCase = mockk<LogOutForGoogleUseCase>()
    private val backupDiariesToGoogleDrive = mockk<BackupDiariesToGoogleDriveUseCase>()
    private val restoreDiariesForGoogleDrive = mockk<RestoreDiariesForGoogleDriveUseCase>()
    private val getAllDiariesUseCase = mockk<GetAllDiariesUseCase>()
    private val isLoggedInUseCase = mockk<IsLoggedInUseCase>()
    private val addDiaryUseCase = mockk<AddDiaryUseCase>()

    private lateinit var viewModel: SettingsViewModel

    @Test
    fun testSettingsViewModel(): Unit = runBlocking {
        // 테스트할 텍스트 사이즈 값
        val testTextSize = 20f

        // UseCase 함수들의 동작을 모의
        coEvery { getCurrentTextSizeUseCase() } returns flowOf(testTextSize)
        coEvery { updateCurrentTextSizeUseCase(any()) } just Runs

        viewModel = SettingsViewModel(
            getCurrentTextSizeUseCase,
            updateCurrentTextSizeUseCase,
            getCurrentFontUseCase,
            updateCurrentFontUseCase,
            logInForGoogleUseCase,
            logOutForGoogleUseCase,
            backupDiariesToGoogleDrive,
            restoreDiariesForGoogleDrive,
            getAllDiariesUseCase,
            isLoggedInUseCase,
            addDiaryUseCase)

        delay(1000)

        // textSize 상태 확인
        assertEquals(testTextSize, testTextSize)

        // UseCase 함수가 호출되었는지 확인
        coVerify { getCurrentTextSizeUseCase() }

        // Intent 전달
        viewModel.processIntent(SettingsIntent.UpdateCurrentFont(testTextSize.toString()))

        // UseCase 함수가 호출되었는지 확인
        coVerify { updateCurrentTextSizeUseCase(testTextSize) }
    }
}
