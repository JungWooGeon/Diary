package com.pass.diary.presentation.state

import com.pass.diary.data.entity.Diary

sealed class TimelineState {
    object Loading : TimelineState()
    data class Success(val diaries: List<Diary>) : TimelineState()
    data class Error(val error: Throwable) : TimelineState()
}