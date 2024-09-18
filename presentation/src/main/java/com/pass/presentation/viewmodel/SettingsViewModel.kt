package com.pass.presentation.viewmodel

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
import com.pass.presentation.sideeffect.SettingSideEffect
import com.pass.presentation.state.screen.SettingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
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
) : ViewModel(), ContainerHost<SettingState, SettingSideEffect> {

    override val container: Container<SettingState, SettingSideEffect> = container(
        initialState = SettingState()
    )

    init {
        // text size collect
        intent {
            getCurrentTextSizeUseCase().collect { newSize ->
                reduce { state.copy(textSize = newSize) }
            }
        }

        // font collect
        intent {
            getCurrentFontUseCase().collect { newFont ->
                reduce { state.copy(textFont = newFont) }
            }
        }

        // 구글 로그인 정보 초기화
        checkIsLoggedIn()
    }

    fun processIntent(intent: SettingsIntent) = intent {
        when (intent) {
            is SettingsIntent.OnNavigateSettingsScreen -> reduce {
                state.copy(settingRouteState = intent.state)
            }

            is SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen -> reduce {
                state.copy(isOpenConfirmBackUpDialog = intent.isOpen)
            }

            is SettingsIntent.UpdateBackUpLoadingState -> reduce {
                state.copy(isBackUpLoading = intent.isLoading)
            }

            is SettingsIntent.UpdateCurrentTextSize -> {
                updateCurrentTextSizeUseCase(intent.textSize)
            }

            is SettingsIntent.UpdateCurrentFont -> {
                updateCurrentFontUseCase(intent.font)
            }

            is SettingsIntent.Login -> {
                reduce { state.copy(isBackUpLoading = true) }
                if (intent.activityResultData != null) {
                    val isSuccess = logInForGoogleUseCase(intent.activityResultData).first()
                    if (isSuccess) {
                        postSideEffect(SettingSideEffect.Toast("로그인을 완료하였습니다."))
                        checkIsLoggedIn()
                    } else {
                        postSideEffect(SettingSideEffect.Toast("로그인에 실패하였습니다."))
                    }
                } else {
                    postSideEffect(SettingSideEffect.Toast("로그인에 실패하였습니다."))
                }
                reduce { state.copy(isBackUpLoading = false) }
            }

            is SettingsIntent.Logout -> {
                viewModelScope.launch(Dispatchers.IO) {
                    logOutForGoogleUseCase()
                    postSideEffect(SettingSideEffect.Toast("로그아웃을 완료하였습니다."))
                    checkIsLoggedIn()
                    reduce { state.copy(isLoggedIn = false) }
                }
            }

            is SettingsIntent.Backup -> {
                reduce {
                    state.copy(
                        isOpenConfirmBackUpDialog = false,
                        isBackUpLoading = true
                    )
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        backupDiariesToGoogleDrive(getAllDiariesUseCase())
                        postSideEffect(SettingSideEffect.Toast("백업을 완료하였습니다."))
                    } catch (e: UserRecoverableAuthIOException) {
                        reduce { state.copy(drivePendingActionIntent = SettingsIntent.Backup) } // 권한 허용 후 다시 실행할 작업 설정
                        postSideEffect(SettingSideEffect.RequestGoogleDrivePermission(error = e.intent))
                    } catch (e: Exception) {
                        postSideEffect(SettingSideEffect.Toast("백업에 실패하였습니다."))
                    } finally {
                        reduce { state.copy(isBackUpLoading = false) }
                    }
                }
            }

            is SettingsIntent.Restore -> {
                reduce { state.copy(isBackUpLoading = true) }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val diaries = restoreDiariesForGoogleDrive()

                        if (diaries == null) {
                            // 복구할 데이터 없음
                            postSideEffect(SettingSideEffect.Toast("복원할 데이터가 존재하지 않습니다."))
                        } else {
                            // 복원 완료하여 Room 에 저장
                            diaries.forEach { addDiaryUseCase(it) }
                            postSideEffect(SettingSideEffect.Toast("복원을 완료하였습니다."))
                        }
                    } catch (e: UserRecoverableAuthIOException) {
                        reduce { state.copy(drivePendingActionIntent = SettingsIntent.Restore) } // 권한 허용 후 다시 실행할 작업 설정
                        postSideEffect(SettingSideEffect.RequestGoogleDrivePermission(error = e.intent))
                    } catch (e: Exception) {
                        postSideEffect(SettingSideEffect.Toast("복원에 실패하였습니다."))
                    } finally {
                        reduce { state.copy(isBackUpLoading = false) }
                    }
                }
            }
        }
    }

    private fun checkIsLoggedIn() = intent {
        val isLoggedIn = isLoggedInUseCase()
        reduce { state.copy(isLoggedIn = isLoggedIn) }
    }
}