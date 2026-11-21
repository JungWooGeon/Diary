package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pass.domain.usecase.settings.password.GetCurrentPasswordUseCase
import com.pass.presentation.intent.MainIntent
import com.pass.presentation.sideeffect.MainSideEffect
import com.pass.presentation.state.screen.LockState
import com.pass.presentation.state.screen.MainState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getCurrentPasswordUseCase: GetCurrentPasswordUseCase
): ViewModel(), ContainerHost<MainState, MainSideEffect> {

    override val container: Container<MainState, MainSideEffect> = container(
        initialState = MainState(
            password = "",
            lock = LockState.None
        )
    )

    init {
        intent {
            val password = getCurrentPasswordUseCase().first()
            reduce {
                state.copy(
                    password = password,
                    lock = if (password == "") LockState.UnLock else LockState.Lock
                )
            }
        }
    }

    fun processIntent(intent: MainIntent) = intent {
        when (intent) {
            MainIntent.UnLock -> reduce {
                state.copy(lock = LockState.UnLock)
            }
        }
    }
}