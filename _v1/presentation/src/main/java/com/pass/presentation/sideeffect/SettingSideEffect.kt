package com.pass.presentation.sideeffect

import android.content.Intent

sealed class SettingSideEffect {
    data class Toast(val text: String) : SettingSideEffect()
    data class RequestGoogleDrivePermission(val error: Intent) : SettingSideEffect()
}