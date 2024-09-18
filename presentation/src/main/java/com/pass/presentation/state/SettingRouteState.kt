package com.pass.presentation.state

sealed class SettingRouteState {
    data object DefaultSettingRoute: SettingRouteState()
    data object FontSettingRoute: SettingRouteState()
    data object NotificationSettingRoute: SettingRouteState()
    data object ScreenLockSettingRoute: SettingRouteState()
    data object StartDateSettingRoute: SettingRouteState()
    data object BackupSettingRoute: SettingRouteState()
    data object LicenseSettingRoute: SettingRouteState()
}