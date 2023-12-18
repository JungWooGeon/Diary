package com.pass.presentation.intent

sealed class TimelineIntent {
    data class LoadDiaries(val year: String, val month: String) : TimelineIntent()

    data class OnDateSelected(val selectedMonth: Int) : TimelineIntent()
    data class UpdateDatePickerYear(val year: Int) : TimelineIntent()
    data class UpdateDatePickerDialogIsOpen(val isOpen: Boolean) : TimelineIntent()

    data object OnCompleteShowToastErrorMessage : TimelineIntent()
}