package com.pass.presentation.state

sealed class LoadingState {
    data object Loading : LoadingState()
    data object Complete : LoadingState()
    data object Standby : LoadingState()
    data class Error(val error: Throwable) : LoadingState()
}