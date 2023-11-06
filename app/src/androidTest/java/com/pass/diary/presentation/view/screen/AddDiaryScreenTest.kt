package com.pass.diary.presentation.view.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.AddDiaryIntent
import com.pass.diary.presentation.state.AddDiaryState
import com.pass.diary.presentation.viewmodel.AddDiaryViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AddDiaryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: AddDiaryViewModel = mockk(relaxed = true)

    private val diary = Diary(
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

    // Standby 상태 테스트
    @Test
    fun addDiaryScreenDisplaysStandbyState() {
        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = null, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("내용 요약하기").assertIsDisplayed()
    }

    // Loading 상태 테스트
    @Test
    fun addDiaryScreenDisplaysLoadingState() {
        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Loading)

        composeTestRule.setContent {
            AddDiaryScreen(diary = null, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("LoadingIndicator").assertIsDisplayed()
    }

    // Error 상태 테스트
    @Test
    fun addDiaryScreenDisplaysErrorState() {
        val errorMessage = "An error occurred"
        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Error(Exception(errorMessage)))

        composeTestRule.setContent {
            AddDiaryScreen(diary = null, viewModel = viewModel)
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    // '일기 추가 화면'에서 '완료' 버튼 클릭 시 일기 정보가 올바르게 저장되고, Complete 상태까지 도달하는지 테스트
    @Test
    fun addDiaryScreenCallsOnAddDiaryWhenDiaryIsProvided() {
        // viewModel의 AddDiary 함수가 호출되었을 때 state 을 Complete 만 나오도록 설정
        every { viewModel.processIntent(AddDiaryIntent.AddDiary(diary)) } answers {
            every { viewModel.state } returns MutableStateFlow(AddDiaryState.Complete)
        }

        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = null, viewModel = viewModel)
        }

        // '완료' 텍스트 클릭
        composeTestRule.onNodeWithText("완료").performClick()

        // state 가 Complete 로 변경되었는지 확인
        assert(viewModel.state.value is AddDiaryState.Complete)
    }

    // '추가' 화면인지 테스트
    @Test
    fun addDiaryScreenDisplaysCorrectIconWhenDiaryIsNull() {
        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = null, viewModel = viewModel)
        }

        // diary가 null이 아닐 때 우측 상단 아이콘이 '완료' 텍스트인지 확인
        composeTestRule.onNodeWithText("완료").assertExists()
    }

    // '편집' 화면인지 테스트
    @Test
    fun addDiaryScreenDisplaysCorrectIconWhenDiaryIsNotNull() {
        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = diary, viewModel = viewModel)
        }

        // diary가 null일 때 우측 상단 아이콘이 '쓰레기통' 아이콘인지 확인
        composeTestRule.onNodeWithContentDescription("Delete Button").assertExists()
    }

    // '일기 편집 화면'에서 '수정 완료' 클릭 시 일기 정보가 올바르게 수정되고, Complete 상태까지 도달하는지 테스트
    @Test
    fun addDiaryScreenCallsOnUpdateDiaryWhenDiaryIsProvided() {
        // viewModel의 UpdateDiary 함수가 호출되었을 때 state 을 Complete 만 나오도록 설정
        every { viewModel.processIntent(AddDiaryIntent.UpdateDiary(diary)) } answers {
            every { viewModel.state } returns MutableStateFlow(AddDiaryState.Complete)
        }

        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = diary, viewModel = viewModel)
        }

        // '수정 완료' 텍스트 클릭
        composeTestRule.onNodeWithText("수정 완료").performClick()

        // state 가 Complete 로 변경되었는지 확인
        assert(viewModel.state.value is AddDiaryState.Complete)
    }

    // '일기 편집 화면'에서 'Delete Button' 클릭 시 일기 정보가 올바르게 삭제되고, Complete 상태까지 도달하는지 테스트
    @Test
    fun addDiaryScreenCallsOnDeleteDiaryWhenDiaryIsProvided() {
        // viewModel의 DeleteDiary 함수가 호출되었을 때 state 을 Complete 만 나오도록 설정
        every { viewModel.processIntent(AddDiaryIntent.DeleteDiary(diary)) } answers {
            every { viewModel.state } returns MutableStateFlow(AddDiaryState.Complete)
        }

        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)

        composeTestRule.setContent {
            AddDiaryScreen(diary = diary, viewModel = viewModel)
        }

        // 'Delete Button' 텍스트 클릭
        composeTestRule.onNodeWithContentDescription("Delete Button").performClick()

        // state 가 Complete 로 변경되었는지 확인
        assert(viewModel.state.value is AddDiaryState.Complete)
    }
}
