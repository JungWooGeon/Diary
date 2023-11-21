package com.pass.diary.presentation.intent

sealed class SettingsIntent {
    data class GetCurrentFont(val textSize: Float) : SettingsIntent()
}