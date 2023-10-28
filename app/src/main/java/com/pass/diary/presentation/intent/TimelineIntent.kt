package com.pass.diary.presentation.intent

sealed class TimelineIntent {
    data class LoadDiaries(val month: String) : TimelineIntent()
}