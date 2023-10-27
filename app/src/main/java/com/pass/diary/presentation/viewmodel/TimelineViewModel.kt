package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class TimelineViewModel(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val state: StateFlow<TimelineState> = _state

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    fun processIntent(intent: TimelineIntent) {
        when (intent) {
            is TimelineIntent.LoadDiaries -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val diaries = getDiariesByMonthUseCase(intent.month)
                    _state.value = TimelineState.Success(diaries)
                }
            }

            is TimelineIntent.UpdateMonth -> {
                _selectedDate.value = intent.month
            }
        }
    }
}