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
import kotlinx.coroutines.withContext

class CalendarViewModel(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
): ViewModel() {
    private val _state = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val state: StateFlow<TimelineState> = _state

    fun processIntent(intent: TimelineIntent) {
        _state.value = TimelineState.Loading
        when (intent) {
            is TimelineIntent.LoadDiaries -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val diaries = getDiariesByMonthUseCase(intent.month)
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
    }
}