package com.pass.presentation.state

sealed class LoginState {
    // 로그인 성공 후 토스트 메시지 출력 필요 상태
    data object Success : LoginState()

    // 로그인 실패 후 토스트 메시지 출력 필요 상태
    data object Fail : LoginState()

    // 로그인 성공 후 토스트 메시지 출력 완료 상태
    data object SuccessIdle : LoginState()

    // 로그인 실패 후 토스트 메시지 출력 완료 상태
    data object FailIdle : LoginState()
}