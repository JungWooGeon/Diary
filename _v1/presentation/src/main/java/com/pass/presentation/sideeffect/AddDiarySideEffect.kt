package com.pass.presentation.sideeffect

sealed class AddDiarySideEffect {
    data class Toast(val text: String) : AddDiarySideEffect()
    data class ChangeTitle(val text: String) : AddDiarySideEffect()
    data class ChangeContent(val text: String) : AddDiarySideEffect()
}