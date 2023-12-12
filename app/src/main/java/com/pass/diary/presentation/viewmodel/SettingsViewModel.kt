package com.pass.diary.presentation.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.pass.diary.domain.diary.AddDiaryUseCase
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
import com.pass.diary.presentation.state.LoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val isLoggedInUseCase: IsLoggedInUseCase,
    private val addDiaryUseCase: AddDiaryUseCase
) : ViewModel() {

    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> = _textSize

    private val _textFont = MutableStateFlow("default")
    val textFont: StateFlow<String> = _textFont

    private val _loginState = MutableStateFlow<LoginState>(LoginState.FailIdle)
    var loginState: StateFlow<LoginState> = _loginState

    // Google Drive 권한 오류 상태
    val error = MutableStateFlow<Intent?>(null)

    private val _restoreDiariesState = MutableStateFlow<Boolean?>(null)
    var restoreDiariesState: StateFlow<Boolean?> = _restoreDiariesState

    private val _backupDiariesState = MutableStateFlow<Boolean?>(null)
    var backupDiariesState: StateFlow<Boolean?> = _backupDiariesState

    init {
        // 텍스트 크기, 폰트, 구글 로그인 정보 초기화
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
            // 처음 초기화 시 토스트 메시지 출력이 필요없으므로 Idle 상태로 등록
            _loginState.value = if (isLoggedInUseCase()) { LoginState.SuccessIdle } else { LoginState.FailIdle }
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

            is SettingsIntent.Login -> {
                viewModelScope.launch(Dispatchers.IO) {
                    if (intent.activityResultData != null) {
                        logInForGoogleUseCase(intent.activityResultData).collect { isSuccess ->
                            _loginState.value = if (isSuccess) { LoginState.Success } else { LoginState.Fail }
                        }
                    } else {
                        _loginState.value = LoginState.Fail
                    }
                }
            }

            is SettingsIntent.Logout -> {
                viewModelScope.launch(Dispatchers.IO) {
                    logOutForGoogleUseCase()
                    // 로그아웃 출력 메시지는 따로 구성되어 있어 Idle 상태로 등록
                    _loginState.value = LoginState.FailIdle
                }
            }

            is SettingsIntent.Backup -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        backupDiariesToGoogleDrive(getAllDiariesUseCase())
                        _backupDiariesState.value = true
                    } catch (e: UserRecoverableAuthIOException) {
                        error.value = e.intent
                    } catch (e: Exception) {
                        _backupDiariesState.value = false
                    }
                }
            }

            is SettingsIntent.Restore -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val diaries = restoreDiariesForGoogleDrive()

                    if (diaries == null) {
                        // 복구할 데이터 없음
                        _restoreDiariesState.value = false
                    } else {
                        // 복원 완료하여 Room 에 저장
                        diaries.forEach {
                            addDiaryUseCase(it)
                        }
                        _restoreDiariesState.value = true
                    }
                }
            }

            // 토스트 메시지 중복 출력 방지를 위한 state 초기화
            is SettingsIntent.SetNullState -> {
                when (intent.stateIntent) {
                    is SettingsIntent.Restore -> { _restoreDiariesState.value = null }
                    is SettingsIntent.Backup -> { _backupDiariesState.value = null }
                    else -> {  }
                }
            }

            is SettingsIntent.SetLoginState -> {
                _loginState.value = intent.loginState
            }
        }
    }
}