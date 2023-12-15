package com.pass.presentation.intent

import java.time.LocalDate

sealed class AnalysisIntent {
    data class UpdateSelectDate(val date: LocalDate) : AnalysisIntent()
    data class UpdateDatePickerYear(val year: Int) : AnalysisIntent()
    data class UpdateDatePickerDialog(val isOpen: Boolean) : AnalysisIntent()
}