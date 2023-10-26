package com.pass.diary.presentation.viewmodel

import com.pass.diary.data.entity.Diary
import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
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

    @Test
    fun getDiariesByMonthUseCaseIsInvoked() = runBlocking {
        val month = "10"
        val diaries = listOf<Diary>()
        coEvery { getDiariesByMonthUseCase.invoke(month) } returns diaries

        viewModel.processIntent(TimelineIntent.LoadDiaries(month))

        coVerify { getDiariesByMonthUseCase.invoke(month) }

        assertEquals(TimelineState.Success(diaries), viewModel.state.value)
    }
}