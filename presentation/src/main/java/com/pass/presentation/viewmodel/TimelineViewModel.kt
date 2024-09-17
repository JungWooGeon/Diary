package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.sideeffect.TimelineSideEffect
import com.pass.presentation.state.screen.TimelineLoadingState
import com.pass.presentation.state.screen.TimelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel(), ContainerHost<TimelineState, TimelineSideEffect> {

    override val container: Container<TimelineState, TimelineSideEffect> = container(
        initialState = TimelineState()
    )

    fun processIntent(intent: TimelineIntent) = intent {
        when (intent) {
            is TimelineIntent.LoadDiaries -> {
                loadDiaries()
            }

            is TimelineIntent.OnSelectNewMonth -> {
                val newDate = LocalDate.of(state.datePickerYear, intent.month, 1)
                if (newDate.isAfter(LocalDate.now())) {
                    postSideEffect(TimelineSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
                } else {
                    reduce { state.copy(selectedDate = newDate) }
                    loadDiaries()
                }
            }

            is TimelineIntent.UpdateDatePickerYear -> reduce {
                state.copy(datePickerYear = intent.year)
            }

            is TimelineIntent.UpdateDatePickerDialogIsOpen -> reduce {
                state.copy(isOpenDatePicker = intent.isOpen)
            }
        }
    }

    private fun loadDiaries() = intent {
        reduce { state.copy(loading = TimelineLoadingState.Loading) }
        try {
            val diaries = getDiariesByMonthUseCase(state.selectedDate.year.toString(), state.selectedDate.monthValue.toString())
            reduce {
                state.copy(
                    loading = TimelineLoadingState.Success(diaries.sortedBy { -it.day.toInt() })
                )
            }
        } catch (e: Exception) {
            reduce { state.copy(loading = TimelineLoadingState.Error(e)) }
        }
    }
}