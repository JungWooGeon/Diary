package com.pass.presentation.sideeffect

sealed class CalendarSideEffect {
    data class Toast(val text: String) : CalendarSideEffect()
}