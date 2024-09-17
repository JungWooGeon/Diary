package com.pass.presentation.intent

import android.content.Intent
import com.pass.presentation.state.SettingRouteState

sealed class SettingsIntent {
    data class OnNavigateSettingsScreen(val state: SettingRouteState) : SettingsIntent()
    data class UpdateShowConfirmBackUpDialogIsOpen(val isOpen: Boolean) : SettingsIntent()
    data class UpdateBackUpLoadingState(val isLoading: Boolean) : SettingsIntent()

    data class UpdateCurrentTextSize(val textSize: Float) : SettingsIntent()
    data class UpdateCurrentFont(val font: String) : SettingsIntent()
    data class Login(val activityResultData: Intent?) : SettingsIntent()
    data object Logout : SettingsIntent()
    data object Backup : SettingsIntent()
    data object Restore : SettingsIntent()
}