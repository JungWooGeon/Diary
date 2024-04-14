package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.domain.usecase.settings.font.GetCurrentFontUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
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