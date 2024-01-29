package com.pass.presentation.state

import com.pass.domain.entity.Diary

sealed class TimelineState {
    data object Loading : TimelineState()
    data class Success(val diaries: List<Diary>) : TimelineState()
    data class Error(val error: Throwable) : TimelineState()
}