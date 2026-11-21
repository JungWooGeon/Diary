package com.pass.presentation.intent

sealed class TimelineIntent {
    data object LoadDiaries : TimelineIntent()

    data class OnSelectNewMonth(val month: Int) : TimelineIntent()
    data class UpdateDatePickerYear(val year: Int) : TimelineIntent()
    data class UpdateDatePickerDialogIsOpen(val isOpen: Boolean) : TimelineIntent()
}