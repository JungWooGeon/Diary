package com.pass.diary.presentation.intent

sealed class SettingsIntent {
    data class UpdateCurrentTextSize(val textSize: Float) : SettingsIntent()
    data class UpdateCurrentFont(val font: String) : SettingsIntent()
}