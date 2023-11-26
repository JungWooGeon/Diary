package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.settings.font.GetCurrentFontUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(
    private val getCurrentFontUseCase: GetCurrentFontUseCase
): ViewModel() {
    private val _currentFont = MutableStateFlow("default")
    val currentFont: StateFlow<String> = _currentFont

    init {
        getCurrentFont()
    }

    private fun getCurrentFont() {
        viewModelScope.launch(Dispatchers.IO) {
            getCurrentFontUseCase().collect { font ->
                _currentFont.value = font
            }
        }
    }
}