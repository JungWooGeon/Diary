package com.pass.presentation.state.screen

import androidx.compose.runtime.Immutable
import com.pass.presentation.intent.SettingsIntent
import com.pass.presentation.state.route.SettingRouteState

@Immutable
data class SettingState(
    // 설정 화면 (기본, 폰트, 백업 등)
    val settingRouteState: SettingRouteState = SettingRouteState.DefaultSettingRoute,

    // 텍스트 크기
    val textSize: Float = DEFAULT_TEXT_SIZE,

    // 텍스트 폰트
    val textFont: String = DEFAULT_TEXT_FONT,

    // 백업 확인 다이얼로그
    val isOpenConfirmBackUpDialog: Boolean = false,

    // 백업 로딩
    val isBackUpLoading: Boolean = false,

    // 구글 로그인
    val isLoggedIn: Boolean = false,

    val drivePendingActionIntent: SettingsIntent = SettingsIntent.Backup,

    // 비밀번호
    val password: String = ""
)

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

private const val DEFAULT_TEXT_SIZE = 16f
private const val DEFAULT_TEXT_FONT = "default"