package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.CalendarIntent
import com.pass.presentation.sideeffect.CalendarSideEffect
import com.pass.presentation.state.screen.CalendarLoadingState
import com.pass.presentation.state.screen.CalendarState
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
) : ViewModel(), ContainerHost<CalendarState, CalendarSideEffect> {

    override val container: Container<CalendarState, CalendarSideEffect> = container(
        initialState = CalendarState()
    )

    fun processIntent(intent: CalendarIntent) = intent {
        when (intent) {
            is CalendarIntent.LoadDiaries -> {
                loadDiaries()
            }

            is CalendarIntent.UpdateDatePickerIsOpen -> reduce {
                state.copy(isOpenDatePicker = intent.isOpen)
            }

            is CalendarIntent.UpdateDatePickerYear -> reduce {
                state.copy(datePickerYear = intent.newYear)
            }

            is CalendarIntent.OnSelectPreviousMonth -> {
                val newDate = state.selectedDate.minusMonths(1).withDayOfMonth(intent.date)
                reduce {
                    state.copy(
                        selectedDate = newDate,
                        datePickerYear = newDate.year
                    )
                }
                loadDiaries()
            }

            is CalendarIntent.OnSelectNextMonth -> {
                val newDate = state.selectedDate.plusMonths(1).withDayOfMonth(intent.date)
                if (newDate.isAfter(LocalDate.now())) {
                    postSideEffect(CalendarSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
                } else {
                    reduce {
                        state.copy(
                            selectedDate = newDate,
                            datePickerYear = newDate.year
                        )
                    }
                    loadDiaries()
                }
            }

            is CalendarIntent.OnSelectTheCurrentMonthDate -> {
                val newDate = state.selectedDate.withDayOfMonth(intent.date)
                if (newDate.isAfter(LocalDate.now())) {
                    postSideEffect(CalendarSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
                } else {
                    reduce { state.copy(selectedDate = newDate) }
                    loadDiaries()
                }
            }

            is CalendarIntent.OnSelectNewMonth -> {
                val newDate = LocalDate.of(state.datePickerYear, intent.month, 1)
                if (newDate.isAfter(LocalDate.now())) {
                    postSideEffect(CalendarSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
                } else {
                    reduce { state.copy(selectedDate = newDate) }
                    loadDiaries()
                }
            }
        }
    }

    private fun loadDiaries() = intent {
        reduce { state.copy(loading = CalendarLoadingState.Loading) }
        try {
            val diaries = getDiariesByMonthUseCase(state.selectedDate.year.toString(), state.selectedDate.monthValue.toString())
            reduce {
                state.copy(
                    loading = CalendarLoadingState.Success(diaries.sortedBy { -it.day.toInt() })
                )
            }
        } catch (e: Exception) {
            reduce { state.copy(loading = CalendarLoadingState.Error(e)) }
        }
    }
}