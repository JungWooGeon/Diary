package com.pass.presentation.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.GetAllDiariesUseCase
import com.pass.domain.usecase.google.BackupDiariesToGoogleDriveUseCase
import com.pass.domain.usecase.google.IsLoggedInUseCase
import com.pass.domain.usecase.google.LogInForGoogleUseCase
import com.pass.domain.usecase.google.LogOutForGoogleUseCase
import com.pass.domain.usecase.google.RestoreDiariesForGoogleDriveUseCase
import com.pass.domain.usecase.settings.font.GetCurrentFontUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentFontUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentTextSizeUseCase
import com.pass.presentation.intent.SettingsIntent
import com.pass.presentation.state.LoginState
import com.pass.presentation.state.SettingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    // 설정 화면 (기본, 폰트, 백업 등)
    private val _settingsScreenState = MutableStateFlow<SettingState>(SettingState.DefaultSetting)
    val settingsScreenState: StateFlow<SettingState> = _settingsScreenState

    // 백업 확인 다이얼로그
    private val _showConfirmBackUpDialogIsOpen = MutableStateFlow(false)
    val showConfirmBackUpDialogIsOpen: StateFlow<Boolean> = _showConfirmBackUpDialogIsOpen

    // 백업 로딩
    private val _backUpLoadingState = MutableStateFlow(false)
    val backUpLoadingState: StateFlow<Boolean> = _backUpLoadingState

    // 텍스트 크기
    private val _textSize = MutableStateFlow(16f)
    val textSize: StateFlow<Float> = _textSize

    // 텍스트 폰트
    private val _textFont = MutableStateFlow("default")
    val textFont: StateFlow<String> = _textFont

    // 구글 로그인
    private val _loginState = MutableStateFlow<LoginState>(LoginState.FailIdle)
    var loginState: StateFlow<LoginState> = _loginState

    // Google Drive 권한 오류 상태
    private val _error = MutableStateFlow<Intent?>(null)
    val error: StateFlow<Intent?> = _error

    // 복원 작업 완료
    private val _restoreDiariesState = MutableStateFlow<Boolean?>(null)
    var restoreDiariesState: StateFlow<Boolean?> = _restoreDiariesState

    // 백업 작업 완료
    private val _backupDiariesState = MutableStateFlow<Boolean?>(null)
    var backupDiariesState: StateFlow<Boolean?> = _backupDiariesState

    // 권한 허용에 따라서 다시 실행할 작업 (백업 / 복원)
    private val _drivePendingActionState = MutableStateFlow<(() -> Unit)?>(null)
    val drivePendingActionState: StateFlow<(() -> Unit)?> = _drivePendingActionState

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
            is SettingsIntent.SetSettingsScreenState -> {
                _settingsScreenState.value = intent.state
            }

            is SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen -> {
                _showConfirmBackUpDialogIsOpen.value = intent.isOpen
            }

            is SettingsIntent.UpdateBackUpLoadingState -> {
                _backUpLoadingState.value = intent.isLoading
            }

            is SettingsIntent.SetNullDrivePendingActionAndErrorState -> {
                _drivePendingActionState.value = null
                _error.value = null
            }

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
                _showConfirmBackUpDialogIsOpen.value = false
                _backUpLoadingState.value = true

                // 권한 허용 후 다시 실행할 작업 설정
                _drivePendingActionState.value = { processIntent(SettingsIntent.Backup) }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        backupDiariesToGoogleDrive(getAllDiariesUseCase())
                        _backupDiariesState.value = true
                    } catch (e: UserRecoverableAuthIOException) {
                        _error.value = e.intent
                    } catch (e: Exception) {
                        _backupDiariesState.value = false
                    }
                }
            }

            is SettingsIntent.Restore -> {
                _backUpLoadingState.value = true

                // 권한 허용 후 다시 실행할 작업 설정
                _drivePendingActionState.value = { processIntent(SettingsIntent.Restore) }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val diaries = restoreDiariesForGoogleDrive()

                        if (diaries == null) {
                            // 복구할 데이터 없음
                            _restoreDiariesState.value = false
                        } else {
                            // 복원 완료하여 Room 에 저장
                            diaries.forEach { addDiaryUseCase(it) }
                            _restoreDiariesState.value = true
                        }

                        _drivePendingActionState.value = null
                    } catch (e: UserRecoverableAuthIOException) {
                        _error.value = e.intent
                    } catch (e: Exception) {
                        _restoreDiariesState.value = false
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