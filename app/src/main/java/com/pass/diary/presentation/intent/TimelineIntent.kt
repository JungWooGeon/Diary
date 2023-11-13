package com.pass.diary.presentation.intent

sealed class TimelineIntent {
    data class LoadDiaries(val year: String, val month: String) : TimelineIntent()
}