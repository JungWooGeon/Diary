package com.pass.presentation.state

sealed class WorkState {
    data object Fail : WorkState()
    data object Success : WorkState()
    data object Standby : WorkState()
}