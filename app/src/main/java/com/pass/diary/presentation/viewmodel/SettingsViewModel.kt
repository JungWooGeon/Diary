package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.settings.font.GetCurrentTextSizeUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentTextSizeUseCase
import com.pass.diary.presentation.intent.SettingsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val updateCurrentTextSizeUseCase: UpdateCurrentTextSizeUseCase
): ViewModel() {

    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> get() = _textSize

    init {
        viewModelScope.launch(Dispatchers.IO) {
            getCurrentTextSizeUseCase().collect { size ->
                _textSize.value = size
            }
        }
    }

    fun processIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.GetCurrentFont -> {
                viewModelScope.launch(Dispatchers.IO) {
                    updateCurrentTextSizeUseCase(intent.textSize)
                }
            }
        }
    }
}