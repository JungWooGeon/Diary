package com.pass.presentation.state.screen

import androidx.compose.runtime.Immutable
import com.pass.domain.entity.Diary
import java.time.LocalDate

@Immutable
data class AnalysisState(
    val loadingState: AnalysisLoadingState = AnalysisLoadingState.Loading,

    // UI 상 에서 선택된 날짜
    val selectedDate: LocalDate = LocalDate.now(),

    // Custom Date Picker 에서 선택된 연도
    val datePickerYear: Int = selectedDate.year,

    // DatePicker show / hide
    val isOpenDatePicker: Boolean = false
)

sealed class AnalysisLoadingState {
    data object Loading : AnalysisLoadingState()
    data class Success(val diaries: List<Diary>) : AnalysisLoadingState()
    data class Error(val error: Throwable) : AnalysisLoadingState()
}