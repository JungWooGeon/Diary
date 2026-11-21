package com.pass.presentation.intent

sealed class MainIntent {
    data object UnLock: MainIntent()
}