package com.pass.presentation.state

sealed class AddDiaryState {
    data object Loading : AddDiaryState()
    data object Complete : AddDiaryState()
    data object Standby : AddDiaryState()
    data class Error(val error: Throwable) : AddDiaryState()
}