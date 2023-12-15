package com.pass.presentation.state

sealed class LoginState {
    data object Success : LoginState()
    data object Fail : LoginState()
    data object SuccessIdle : LoginState()
    data object FailIdle : LoginState()
}