package com.pass.diary.presentation.state

sealed class SettingState {
    data object DefaultSetting: SettingState()
    data object FontSetting: SettingState()
    data object DarkModeSetting: SettingState()
    data object ThemeSetting: SettingState()
    data object NotificationSetting: SettingState()
    data object ScreenLockSetting: SettingState()
    data object StartDateSetting: SettingState()
    data object BackupSetting: SettingState()
    data object PrivacyPolicySetting: SettingState()
    data object LicenseSetting: SettingState()
}