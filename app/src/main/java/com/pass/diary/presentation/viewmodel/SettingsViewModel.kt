package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.settings.font.GetCurrentFontUseCase
import com.pass.diary.domain.settings.font.GetCurrentTextSizeUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentFontUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentTextSizeUseCase
import com.pass.diary.presentation.intent.SettingsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val updateCurrentTextSizeUseCase: UpdateCurrentTextSizeUseCase,
    private val getCurrentFontUseCase: GetCurrentFontUseCase,
    private val updateCurrentFontUseCase: UpdateCurrentFontUseCase
) : ViewModel() {

    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> = _textSize

    private val _textFont = MutableStateFlow("default")
    val textFont: StateFlow<String> = _textFont

    init {
        viewModelScope.launch(Dispatchers.IO) {
            getCurrentTextSizeUseCase().collect { size ->
                _textSize.value = size
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            getCurrentFontUseCase().collect { font ->
                _textFont.value = font
            }
        }
    }

    fun processIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateCurrentTextSize -> {
                viewModelScope.launch(Dispatchers.IO) {
                    updateCurrentTextSizeUseCase(intent.textSize)
                }
            }

            is SettingsIntent.UpdateCurrentFont -> {
                viewModelScope.launch(Dispatchers.IO) {
                    updateCurrentFontUseCase(intent.font)
                }
            }
        }
    }
}