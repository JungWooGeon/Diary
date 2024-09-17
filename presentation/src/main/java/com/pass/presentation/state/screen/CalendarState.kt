package com.pass.presentation.state.screen

import androidx.compose.runtime.Immutable
import com.pass.domain.entity.Diary
import java.time.LocalDate

@Immutable
data class CalendarState (
    val loading: CalendarLoadingState = CalendarLoadingState.Loading,

    // UI 상 에서 선택된 날짜
    val selectedDate: LocalDate = LocalDate.now(),

    // Custom Date Picker 에서 선택된 연도
    val datePickerYear: Int = selectedDate.year,

    // DatePicker show / hide
    val isOpenDatePicker: Boolean = false
)

sealed class CalendarLoadingState {
    data object Loading : CalendarLoadingState()
    data class Success(val diaries: List<Diary>) : CalendarLoadingState()
    data class Error(val error: Throwable) : CalendarLoadingState()
}
