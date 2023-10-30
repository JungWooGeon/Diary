package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.diary.AddDiaryUseCase
import com.pass.diary.presentation.intent.AddDiaryIntent
import com.pass.diary.presentation.state.AddDiaryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.Exception

class AddDiaryViewModel(
    private val addDiaryUseCase: AddDiaryUseCase
): ViewModel() {

    private val _state = MutableStateFlow<AddDiaryState>(AddDiaryState.Standby)
    val state: StateFlow<AddDiaryState> = _state

    fun processIntent(intent: AddDiaryIntent) {
        when (intent) {
            is AddDiaryIntent.AddDiary -> {
                _state.value = AddDiaryState.Loading

                try {
                    viewModelScope.launch(Dispatchers.IO) {
                        addDiaryUseCase(intent.diary)
                        _state.value = AddDiaryState.Complete
                    }
                } catch (e: Exception) {
                    _state.value = AddDiaryState.Error(e)
                }
            }
        }
    }
}