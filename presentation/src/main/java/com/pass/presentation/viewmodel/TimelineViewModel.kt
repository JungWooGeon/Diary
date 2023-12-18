package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.state.TimelineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TimelineViewModel(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel() {

    private val _timeLineState = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val timeLineState: StateFlow<TimelineState> = _timeLineState

    // UI 상 에서 선택된 날짜
    private val _selectedDateState = MutableStateFlow(LocalDate.now())
    val selectedDateState: StateFlow<LocalDate> = _selectedDateState

    // Custom Date Picker 에서 선택된 연도
    private val _datePickerYearState = MutableStateFlow(selectedDateState.value.year)
    val datePickerYearState: StateFlow<Int> = _datePickerYearState

    // DatePicker show / hide
    private val _isDatePickerOpenState = MutableStateFlow(false)
    val isDatePickerOpenState: StateFlow<Boolean> = _isDatePickerOpenState

    // 날짜 선택 예외 처리
    private val _selectedDateErrorState = MutableStateFlow(false)
    val selectedDateErrorState: StateFlow<Boolean> = _selectedDateErrorState

    fun processIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.LoadDiaries -> {
                _timeLineState.value = TimelineState.Loading
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val diaries = getDiariesByMonthUseCase(intent.year, intent.month)
                        withContext(Dispatchers.Main) {
                            _timeLineState.value = TimelineState.Success(diaries.sortedBy { -it.day.toInt() })
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            _timeLineState.value = TimelineState.Error(e)
                        }
                    }
                }
            }

            is TimelineIntent.OnDateSelected -> {
                val newDate = LocalDate.of(datePickerYearState.value, intent.selectedMonth, 1)
                if (newDate.isAfter(LocalDate.now())) {
                    _selectedDateErrorState.value = true
                } else {
                    _selectedDateState.value = newDate
                }
            }

            is TimelineIntent.UpdateDatePickerYear -> {
                _datePickerYearState.value = intent.year
            }

            is TimelineIntent.UpdateDatePickerDialogIsOpen -> {
                _isDatePickerOpenState.value = intent.isOpen
            }

            is TimelineIntent.OnCompleteShowToastErrorMessage -> {
                _selectedDateErrorState.value = false
            }
        }
    }
}