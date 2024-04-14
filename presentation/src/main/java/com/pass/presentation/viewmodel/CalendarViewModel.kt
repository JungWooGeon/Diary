package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.CalendarIntent
import com.pass.presentation.state.TimelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel() {

    // 현재 화면 상태 (standby, loading, error)
    private val _calendarState = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val calendarState: StateFlow<TimelineState> = _calendarState

    // UI 상 에서 선택된 날짜
    private val _selectedDateState = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDateState: StateFlow<LocalDate> = _selectedDateState

    // DatePicker show / hide
    private val _isDatePickerOpenState = MutableStateFlow(false)
    val isDatePickerOpenState: StateFlow<Boolean> = _isDatePickerOpenState

    // Custom Date Picker 에서 선택된 연도
    private val _datePickerYearState = MutableStateFlow(selectedDateState.value.year)
    val datePickerYearState: StateFlow<Int> = _datePickerYearState

    // 날짜 선택 예외 처리
    private val _selectedDateErrorState = MutableStateFlow(false)
    val selectedDateErrorState: StateFlow<Boolean> = _selectedDateErrorState

    fun processIntent(intent: CalendarIntent) {
        when (intent) {
            is CalendarIntent.LoadDiaries -> {
                loadDiaries()
            }

            is CalendarIntent.UpdateDatePickerIsOpen -> {
                _isDatePickerOpenState.value = intent.isOpen
            }

            is CalendarIntent.UpdateDatePickerYear -> {
                _datePickerYearState.value = intent.newYear
            }

            is CalendarIntent.OnSelectPreviousMonth -> {
                _selectedDateState.value = selectedDateState.value.minusMonths(1).withDayOfMonth(intent.date)
                _datePickerYearState.value = selectedDateState.value.year
                loadDiaries()
            }

            is CalendarIntent.OnSelectNextMonth -> {
                val newDate = selectedDateState.value.plusMonths(1).withDayOfMonth(intent.date)
                if (newDate.isAfter(LocalDate.now())) {
                    _selectedDateErrorState.value = true
                } else {
                    _selectedDateState.value = newDate
                    _datePickerYearState.value = selectedDateState.value.year
                    loadDiaries()
                }
            }

            is CalendarIntent.OnSelectTheCurrentMonthDate -> {
                val newDate = selectedDateState.value.withDayOfMonth(intent.date)
                if (newDate.isAfter(LocalDate.now())) {
                    _selectedDateErrorState.value = true
                } else {
                    _selectedDateState.value = newDate
                    loadDiaries()
                }
            }

            is CalendarIntent.OnSelectNewMonth -> {
                val newDate = LocalDate.of(datePickerYearState.value, intent.month, 1)
                if (newDate.isAfter(LocalDate.now())) {
                    _selectedDateErrorState.value = true
                } else {
                    _selectedDateState.value = newDate
                    loadDiaries()
                }
            }

            is CalendarIntent.OnCompleteShowToastErrorMessage -> {
                _selectedDateErrorState.value = false
            }
        }
    }

    private fun loadDiaries() {
        _calendarState.value = TimelineState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val diaries = getDiariesByMonthUseCase(selectedDateState.value.year.toString(), selectedDateState.value.monthValue.toString())
                withContext(Dispatchers.Main) {
                    _calendarState.value = TimelineState.Success(diaries.sortedBy { -it.day.toInt() })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _calendarState.value = TimelineState.Error(e)
                }
            }
        }
    }
}