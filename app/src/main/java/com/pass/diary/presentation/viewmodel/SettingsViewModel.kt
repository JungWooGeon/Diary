package com.pass.diary.presentation.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.firebase.auth.FirebaseAuth
import com.pass.diary.domain.diary.GetAllDiariesUseCase
import com.pass.diary.domain.google.BackupDiariesToGoogleDriveUseCase
import com.pass.diary.domain.google.IsLoggedInUseCase
import com.pass.diary.domain.google.LogInForGoogleUseCase
import com.pass.diary.domain.google.LogOutForGoogleUseCase
import com.pass.diary.domain.google.RestoreDiariesForGoogleDriveUseCase
import com.pass.diary.domain.settings.font.GetCurrentFontUseCase
import com.pass.diary.domain.settings.font.GetCurrentTextSizeUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentFontUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentTextSizeUseCase
import com.pass.diary.presentation.intent.SettingsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val getCurrentTextSizeUseCase: GetCurrentTextSizeUseCase,
    private val updateCurrentTextSizeUseCase: UpdateCurrentTextSizeUseCase,
    private val getCurrentFontUseCase: GetCurrentFontUseCase,
    private val updateCurrentFontUseCase: UpdateCurrentFontUseCase,
    private val logInForGoogleUseCase: LogInForGoogleUseCase,
    private val logOutForGoogleUseCase: LogOutForGoogleUseCase,
    private val backupDiariesToGoogleDrive: BackupDiariesToGoogleDriveUseCase,
    private val restoreDiariesForGoogleDrive: RestoreDiariesForGoogleDriveUseCase,
    private val getAllDiariesUseCase: GetAllDiariesUseCase,
    private val isLoggedInUseCase: IsLoggedInUseCase
) : ViewModel() {

    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> = _textSize

    private val _textFont = MutableStateFlow("default")
    val textFont: StateFlow<String> = _textFont

    private val _loginState = MutableStateFlow(false)
    var loginState: StateFlow<Boolean> = _loginState

    val error = MutableStateFlow<Intent?>(null)

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

        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = isLoggedInUseCase()
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

    fun backup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupDiariesToGoogleDrive(getAllDiariesUseCase())
            } catch (e: UserRecoverableAuthIOException) {
                Log.d("오류", "viewModel 까지 왔다")
                error.value = e.intent
            }
        }
    }

    fun restore() {
        viewModelScope.launch(Dispatchers.IO) {
            restoreDiariesForGoogleDrive().forEach {
                Log.d("성공", it.content)
            }
        }
    }
}