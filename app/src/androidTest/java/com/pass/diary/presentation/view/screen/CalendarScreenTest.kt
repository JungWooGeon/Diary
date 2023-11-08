package com.pass.diary.presentation.view.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
import com.pass.diary.presentation.viewmodel.CalendarViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CalendarScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: CalendarViewModel = mockk(relaxed = true)

    // 현재 날짜로 모킹 데이터 설정
    private val mockData = listOf(
        Diary(
            null,
            LocalDate.now().year.toString(),
            LocalDate.now().monthValue.toString(),
            LocalDate.now().dayOfMonth.toString(),
            Constants.DAY_OF_WEEK_TO_KOREAN[LocalDate.now().dayOfWeek.toString()].toString(),
            2131689482,
            null,
            null,
            null,
            null,
            ""
        )
    )

    @Before
    fun setup() {
        every { viewModel.state } returns MutableStateFlow<TimelineState>(TimelineState.Success(mockData))
    }

    // '월' 변경 시 잘 동작하는지 테스트
    @Test
    fun testChangeMonth() {
        val slot = slot<TimelineIntent.LoadDiaries>()
        val initialMonth = 10

        // 현재 월에 대한 '일기 목록'을 읽을 경우 "initialMonth + 1 처리"
        every { viewModel.processIntent(capture(slot)) } answers {
            initialMonth + 1
        }

        // 여기서 viewModel.processIntent(TimelineIntent.LoadDiaries) 1번째 실행
        composeTestRule.setContent {
            CalendarScreen(viewModel = viewModel)
        }

        // '>' 버튼 클릭 -> 다음 달로 변경
        // 여기서 viewModel.processIntent(TimelineIntent.LoadDiaries) 2번째 실행
        composeTestRule.onNodeWithContentDescription("다음 달").performClick()

        verify { viewModel.processIntent(any()) }

        // 총 2번 실행되어 initialMonth 값이 2 증가하면 통과
        val validationText = (initialMonth + 2).toString() + "월"
        composeTestRule.onNodeWithText(validationText).assertIsDisplayed()
    }

    // 현재 날짜로 등록된 '일기' 정보가 있을 때, UI 리스트에 잘 반영되는지 테스트
    @Test
    fun testViewDiaryListWithCurrentDate() {
        composeTestRule.setContent {
            CalendarScreen(viewModel = viewModel)
        }

        // 현재 날짜이므로 리스트 목록에 현재 날짜가 한글로 표시되어야 하는 부분 테스트
        mockData.forEach { data ->
            val testString = data.year + "년 " + data.month + "월 " + data.day + "일 " + data.dayOfWeek + "요일"
            composeTestRule.onNodeWithText(testString).assertExists()
        }
    }
}