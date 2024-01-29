package com.pass.presentation.viewmodel

import com.pass.domain.entity.Diary
import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.DeleteDiaryUseCase
import com.pass.domain.usecase.diary.SummaryDiaryUseCase
import com.pass.domain.usecase.diary.UpdateDiaryUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.presentation.intent.AddDiaryIntent
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class AddDiaryViewModelTest {
    private lateinit var viewModel: AddDiaryViewModel
    private val addDiaryUseCase = mockk<AddDiaryUseCase>()
    private val updateDiaryUseCase = mockk<UpdateDiaryUseCase>()
    private val deleteDiaryUseCase = mockk<DeleteDiaryUseCase>()
    private val getCurrentTextSizeUseCase = mockk<GetCurrentTextSizeUseCase>()
    private val summaryDiaryUseCase = mockk<SummaryDiaryUseCase>()

    @Before
    fun setup() {
        viewModel = AddDiaryViewModel(addDiaryUseCase, updateDiaryUseCase, deleteDiaryUseCase, getCurrentTextSizeUseCase, summaryDiaryUseCase)
    }

    @Test
    fun testAddSubmit(): Unit = runBlocking {
        val diary = Diary(
            null,
            "2023",
            "10",
            "30",
            "일",
            null,
            null,
            null,
            null,
            "",
            "일기 내용"
        )

        coEvery { addDiaryUseCase(diary) } just Runs

        viewModel.processIntent(AddDiaryIntent.AddDiary)

        // 한 번만 수행되었는지 확인
        coVerify(exactly = 1) { addDiaryUseCase(diary) }
    }

    @Test
    fun testUpdateSubmit(): Unit = runBlocking {
        val month = "11"
        val diary = Diary(
            null,
            "2023",
            month,
            "06",
            "월",
            null,
            null,
            null,
            null,
            "",
            "일기 내용 테스트 11/06"
        )

        // addDiary 실행 시 실행만 되게 적용
        coEvery { updateDiaryUseCase(diary) } just Runs

        viewModel.processIntent(AddDiaryIntent.UpdateDiary)

        // 한 번만 수행되었는지 확인
        coVerify(exactly = 1) { updateDiaryUseCase(diary) }
    }

    @Test
    fun testDeleteSubmit(): Unit = runBlocking {
        val month = "11"
        val diary = Diary(
            null,
            "2023",
            month,
            "06",
            "월",
            null,
            null,
            null,
            null,
            "",
            "일기 내용 테스트 11/06"
        )

        // addDiary 실행 시 실행만 되게 적용
        coEvery { deleteDiaryUseCase(diary) } just Runs

        viewModel.processIntent(AddDiaryIntent.DeleteDiary)

        // 한 번만 수행되었는지 확인
        coVerify(exactly = 1) { deleteDiaryUseCase(diary) }
    }
}
