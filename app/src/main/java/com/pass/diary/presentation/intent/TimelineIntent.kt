package com.pass.diary.presentation.intent

import java.time.LocalDate

sealed class TimelineIntent {
    data class LoadDiaries(val month: String) : TimelineIntent()
    data class UpdateMonth(val month: LocalDate) : TimelineIntent()
}