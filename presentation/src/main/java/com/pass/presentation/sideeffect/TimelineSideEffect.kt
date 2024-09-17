package com.pass.presentation.sideeffect

sealed class TimelineSideEffect {
    data class Toast(val text: String) : TimelineSideEffect()
}