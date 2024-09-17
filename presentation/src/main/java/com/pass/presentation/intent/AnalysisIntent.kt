package com.pass.presentation.intent

sealed class AnalysisIntent {
    data object LoadDiaries : AnalysisIntent()

    data class OnDateSelected(val selectedMonth: Int) : AnalysisIntent()
    data class UpdateDatePickerYear(val year: Int) : AnalysisIntent()
    data class UpdateDatePickerDialog(val isOpen: Boolean) : AnalysisIntent()

    data object OnSelectPreviousMonth : AnalysisIntent()
    data object OnSelectNextMonth : AnalysisIntent()
}