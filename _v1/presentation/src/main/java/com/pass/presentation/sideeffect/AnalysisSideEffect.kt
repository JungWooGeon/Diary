package com.pass.presentation.sideeffect

sealed class AnalysisSideEffect {
    data class Toast(val text: String) : AnalysisSideEffect()
}