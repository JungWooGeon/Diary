package com.pass.presentation.state.screen

import androidx.compose.runtime.Immutable

@Immutable
data class MainState(
    val password: String,
    val lock: LockState
)

enum class LockState {
    None, Lock, UnLock
}