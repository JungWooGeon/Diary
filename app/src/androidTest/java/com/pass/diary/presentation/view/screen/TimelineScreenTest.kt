package com.pass.diary.presentation.view.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.state.TimelineState
import com.pass.diary.presentation.viewmodel.TimelineViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimelineScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // viewModel 의 state 를 설정하기 위해 mock 객체 사용
    private val viewModel: TimelineViewModel = mockk(relaxed = true)

    @Test
    fun timelineScreenDisplaysLoadingState() {
        // viewModel 의 기본 state 를 "Loading" 으로 변경
        every { viewModel.state } returns MutableStateFlow(TimelineState.Loading)

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
                null,
                "일기 내용 테스트 10/26"
            )
        )

        // viewModel 의 state 를 "Success" 로 변경
        every { viewModel.state } returns MutableStateFlow(TimelineState.Success(diaries))

        composeTestRule.setContent {
            TimelineScreen(viewModel = viewModel)
        }

        // UI 변경사항이 모두 반영될 때까지 대기
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("LoadingIndicator").assertDoesNotExist()
        composeTestRule.onNodeWithText("20231026목").assertIsDisplayed()
        composeTestRule.onNodeWithText("일기 내용 테스트 10/26").assertIsDisplayed()
    }
}
