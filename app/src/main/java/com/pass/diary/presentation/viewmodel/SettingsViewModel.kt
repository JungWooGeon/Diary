package com.pass.diary.presentation.viewmodel

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
        activityResult: ActivityResult,
        onSuccess: () -> Unit,
        onFail: () -> Unit
    ) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                .getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onSuccess()
                        _loginState.value = true
                    } else {
                        Log.d("로그인 실패", "아이디 비밀번호 불일치")
                        onFail()
                    }
                }
        } catch (e: Exception) {
            Log.d("로그인 실패", e.message.toString())
            onFail()
        }
    }

    fun logout() {
        _loginState.value = false
        FirebaseAuth.getInstance().signOut()
    }
}