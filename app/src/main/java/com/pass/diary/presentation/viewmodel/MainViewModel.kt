package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pass.diary.presentation.intent.MainIntent
import com.pass.diary.presentation.state.MainState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainState(MainIntent.Timeline))
    val state: StateFlow<MainState> = _state

    fun onNavItemSelected(intent: MainIntent) {
        _state.value = MainState(intent)
    }
}