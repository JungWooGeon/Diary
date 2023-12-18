package com.pass.presentation.view.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pass.domain.model.Diary
import com.pass.presentation.intent.AddDiaryIntent
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.state.AddDiaryState
import com.pass.presentation.state.TimelineState
import com.pass.presentation.viewmodel.TimelineViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimelineScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // viewModel 의 state 를 설정하기 위해 mock 객체 사용
    private val viewModel: TimelineViewModel = mockk(relaxed = true)

    @Before
    fun setup() {
        every { viewModel.selectedDateState } returns MutableStateFlow(LocalDate.of(2023, 10, 26))
        every { viewModel.datePickerYearState } returns MutableStateFlow(2023)
        every { viewModel.isDatePickerOpenState } returns MutableStateFlow(false)
        every { viewModel.selectedDateErrorState } returns MutableStateFlow(false)
    }

    @Test
    fun timelineScreenDisplaysLoadingState() {
        // viewModel 의 기본 state 를 "Loading" 으로 변경
        every { viewModel.timeLineState } returns MutableStateFlow(TimelineState.Loading)

        composeTestRule.setContent {
            TimelineScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    @Test
    fun timelineScreenDisplaysDiariesWhenSuccess() {
        val diaries = listOf(
            Diary(
                null,
                "2023",
                "10",
                "26",
                "목",
                null,
                null,
                null,
                null,
                "일기 내용 테스트 10/26",
                ""
            )
        )

        // viewModel 의 state 를 "Success" 로 변경
        every { viewModel.timeLineState } returns MutableStateFlow(TimelineState.Success(diaries))

        composeTestRule.setContent {
            TimelineScreen(viewModel = viewModel)
        }

        // UI 변경사항이 모두 반영될 때까지 대기
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("LoadingIndicator").assertDoesNotExist()
        composeTestRule.onNodeWithText("2023년 10월 26일 목요일").assertIsDisplayed()
        composeTestRule.onNodeWithText("일기 내용 테스트 10/26").assertIsDisplayed()
    }

    @Test
    fun timelineScreenDisplaysErrorState() {
        val errorMessage = "An error occurred"
        every { viewModel.timeLineState } returns MutableStateFlow(
            TimelineState.Error(
                Exception(
                    errorMessage
                )
            )
        )

        composeTestRule.setContent {
            TimelineScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // DatePicker 가 열리는지 테스트
    @Test
    fun timelineScreenOpensDatePicker() {
        val diaries = listOf(
            Diary(
                null,
                "2023",
                "10",
                "26",
                "목",
                null,
                null,
                null,
                null,
                "",
                "일기 내용 테스트 10/26"
            )
        )
        every { viewModel.timeLineState } returns MutableStateFlow(TimelineState.Success(diaries))

        composeTestRule.setContent {
            TimelineScreen(viewModel = viewModel)
        }

        // viewModel의 AddDiary 함수가 호출되었을 때 state 을 Complete 만 나오도록 설정
        every { viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(true)) } answers {
            every { viewModel.isDatePickerOpenState } returns MutableStateFlow(true)
        }

        composeTestRule.onNodeWithContentDescription("DatePicker Button").performClick()
        assertEquals(viewModel.isDatePickerOpenState.value, true)
    }
}
