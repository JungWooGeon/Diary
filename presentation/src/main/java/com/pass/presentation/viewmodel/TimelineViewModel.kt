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

class TimelineViewModel(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<TimelineState>(TimelineState.Loading)
    val state: StateFlow<TimelineState> = _state

    fun processIntent(intent: TimelineIntent) {
        _state.value = TimelineState.Loading
        when (intent) {
            is TimelineIntent.LoadDiaries -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val diaries = getDiariesByMonthUseCase(intent.year, intent.month)
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