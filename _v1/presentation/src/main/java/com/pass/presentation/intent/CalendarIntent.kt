package com.pass.presentation.intent

sealed class CalendarIntent {
    data object LoadDiaries : CalendarIntent()

    data class UpdateDatePickerIsOpen(val isOpen: Boolean) : CalendarIntent()
    data class UpdateDatePickerYear(val newYear: Int) : CalendarIntent()

    data class OnSelectNewMonth(val month: Int) : CalendarIntent()
    data class OnSelectPreviousMonth(val date: Int) : CalendarIntent()
    data class OnSelectNextMonth(val date: Int) : CalendarIntent()
    data class OnSelectTheCurrentMonthDate(val date: Int) : CalendarIntent()
}