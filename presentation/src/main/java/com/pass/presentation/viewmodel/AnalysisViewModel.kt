package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.AnalysisIntent
import com.pass.presentation.state.TimelineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AnalysisViewModel(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
): ViewModel() {

    // 화면 로드
    private val _state = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val state: StateFlow<TimelineState> = _state

    // UI 상 에서 선택된 날짜
    private val _selectedDateState = MutableStateFlow(LocalDate.now())
    val selectedDateState: StateFlow<LocalDate> = _selectedDateState

    // Custom Date Picker 에서 선택된 연도
    private val _datePickerYearState = MutableStateFlow(selectedDateState.value.year)
    val datePickerYearState: StateFlow<Int> = _datePickerYearState

    // DatePicker show / hide
    private val _isDatePickerOpenState = MutableStateFlow(false)
    val isDatePickerOpenState: StateFlow<Boolean> = _isDatePickerOpenState

    // 처음 시작 시 현재 날짜 기반 일기 내용 로드
    init { loadDiaries() }

    fun processIntent(intent: AnalysisIntent) {
        when (intent) {
            is AnalysisIntent.UpdateSelectDate -> {
                _selectedDateState.value = intent.date

                // selectedDate 변경 시마다 diary 업데이트
                loadDiaries()
            }

            is AnalysisIntent.UpdateDatePickerYear -> {
                _datePickerYearState.value = intent.year
            }

            is AnalysisIntent.UpdateDatePickerDialog -> {
                _isDatePickerOpenState.value = intent.isOpen
            }
        }
    }

    private fun loadDiaries() {
        _state.value = TimelineState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val diaries = getDiariesByMonthUseCase(selectedDateState.value.year.toString(), selectedDateState.value.monthValue.toString())
                withContext(Dispatchers.Main) {
                    _state.value = TimelineState.Success(diaries.sortedBy { -it.day.toInt() })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = TimelineState.Error(e)
                }
            }
        }
    }
}