package com.pass.diary.presentation.state

sealed class LoginState {
    object Success : LoginState()
    object Fail : LoginState()
    object SuccessIdle : LoginState()
    object FailIdle : LoginState()
}