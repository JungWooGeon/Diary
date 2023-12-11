package com.pass.diary.presentation.viewmodel

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.pass.diary.domain.google.LogInForGoogleUseCase
import com.pass.diary.domain.google.LogOutForGoogleUseCase
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
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val updateCurrentTextSizeUseCase: UpdateCurrentTextSizeUseCase,
    private val getCurrentFontUseCase: GetCurrentFontUseCase,
    private val updateCurrentFontUseCase: UpdateCurrentFontUseCase,
    private val logInForGoogleUseCase: LogInForGoogleUseCase,
    private val logOutForGoogleUseCase: LogOutForGoogleUseCase
) : ViewModel() {

    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> = _textSize

    private val _textFont = MutableStateFlow("default")
    val textFont: StateFlow<String> = _textFont

    private val _loginState = MutableStateFlow((FirebaseAuth.getInstance().currentUser != null))
    var loginState: StateFlow<Boolean> = _loginState

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

    fun login(
        activityResultData: Intent?,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (activityResultData != null) {
                logInForGoogleUseCase(activityResultData).collect { isSuccess ->
                    _loginState.value = isSuccess
                    withContext(Dispatchers.Main) {
                        if (isSuccess) { onSuccess() } else { onFail() }
                    }
                }
            } else {
                onFail()
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            logOutForGoogleUseCase()
        }

        _loginState.value = false
    }
}