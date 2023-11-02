package com.pass.diary.presentation.view.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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

class AddDiaryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: AddDiaryViewModel = mockk(relaxed = true)

    private val diary = Diary(
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

//    // '일기 추가 화면'에서 '완료' 버튼 클릭 시 일기 정보가 올바르게 저장되고, 화면이 꺼지는 상태까지 동작하는지 테스트
//    @Test
//    fun addDiaryScreenCallsOnAddDiaryWhenDiaryIsProvided() {
//        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Standby)
//
//        composeTestRule.setContent {
//            AddDiaryScreen(diary = null, viewModel = viewModel)
//        }
//
//        composeTestRule.onNodeWithText("완료").performClick()
//
//        // viewModel의 AddDiary 함수가 호출되었는지 확인
//        verify { viewModel.processIntent(AddDiaryIntent.AddDiary(any())) }
//
//        // AddDiary 함수가 호출된 후, addDiaryState 상태가 Complete 상태로 변경되었는지 확인
//        every { viewModel.state } returns MutableStateFlow(AddDiaryState.Complete)
//
//        composeTestRule.onNodeWithText("작성이 완료되었습니다.").assertIsDisplayed()
//    }
}
