package com.pass.diary.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pass.diary.domain.diary.AddDiaryUseCase
import com.pass.diary.domain.diary.DeleteDiaryUseCase
import com.pass.diary.domain.diary.UpdateDiaryUseCase
import com.pass.diary.domain.settings.font.GetCurrentTextSizeUseCase
import com.pass.diary.presentation.intent.AddDiaryIntent
import com.pass.diary.presentation.state.AddDiaryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception

class AddDiaryViewModel(
    private val addDiaryUseCase: AddDiaryUseCase,
    private val updateDiaryUseCase: UpdateDiaryUseCase,
    private val deleteDiaryUseCase: DeleteDiaryUseCase,
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AddDiaryState>(AddDiaryState.Standby)
    val state: StateFlow<AddDiaryState> = _state

    private val _testSizeState = MutableStateFlow(13f)
    val textSizeState: StateFlow<Float> = _testSizeState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            getCurrentTextSizeUseCase().collect { size ->
                _testSizeState.value = size
            }
        }
    }

    fun processIntent(intent: AddDiaryIntent) {
        when (intent) {
            is AddDiaryIntent.AddDiary -> {
                _state.value = AddDiaryState.Loading
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        addDiaryUseCase(intent.diary)
                        withContext(Dispatchers.Main) {
                            _state.value = AddDiaryState.Complete
                        }
                    } catch (e: Exception) {
                        _state.value = AddDiaryState.Error(e)
                    }
                }
            }

            is AddDiaryIntent.UpdateDiary -> {
                _state.value = AddDiaryState.Loading
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        updateDiaryUseCase(intent.diary)
                        withContext(Dispatchers.Main) {
                            _state.value = AddDiaryState.Complete
                        }
                    } catch (e: Exception) {
                        _state.value = AddDiaryState.Error(e)
                    }
                }
            }

            is AddDiaryIntent.DeleteDiary -> {
                _state.value = AddDiaryState.Loading
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        deleteDiaryUseCase(intent.diary)
                        withContext(Dispatchers.Main) {
                            _state.value = AddDiaryState.Complete
                        }
                    } catch (e: Exception) {
                        _state.value = AddDiaryState.Error(e)
                    }
                }
            }
        }
    }
}