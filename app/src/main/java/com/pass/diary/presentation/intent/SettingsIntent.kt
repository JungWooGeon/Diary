package com.pass.diary.presentation.intent

import android.content.Intent
import com.pass.diary.presentation.state.LoginState

sealed class SettingsIntent {
    data class UpdateCurrentTextSize(val textSize: Float) : SettingsIntent()
    data class UpdateCurrentFont(val font: String) : SettingsIntent()
    data class Login(val activityResultData: Intent?) : SettingsIntent()
    data object Logout : SettingsIntent()
    data object Backup : SettingsIntent()
    data object Restore : SettingsIntent()
    data class SetNullState(val stateIntent: SettingsIntent) : SettingsIntent()
    data class SetLoginState(val loginState: LoginState) : SettingsIntent()
}