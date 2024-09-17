package com.pass.presentation.viewmodel

import com.pass.domain.entity.Diary
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.state.screen.TimelineState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class TimelineViewModelTest {

    private lateinit var viewModel: TimelineViewModel
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase = mockk()

    @Before
    fun setup() {
        viewModel = TimelineViewModel(getDiariesByMonthUseCase)
    }

    // process state 변경 테스트
    @Test
    fun getDiariesByMonthUseCaseIsInvoked() = runBlocking {
        val month = "10"
        val diaries = listOf<Diary>()
        coEvery { getDiariesByMonthUseCase.invoke("2023", month) } returns diaries

        viewModel.processIntent(TimelineIntent.LoadDiaries("2023", month))

        coVerify { getDiariesByMonthUseCase.invoke(month) }

        assertEquals(TimelineState.Success(diaries), viewModel.state.value)
    }
}