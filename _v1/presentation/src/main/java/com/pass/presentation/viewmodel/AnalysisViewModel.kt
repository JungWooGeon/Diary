package com.pass.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.presentation.intent.AnalysisIntent
import com.pass.presentation.sideeffect.AnalysisSideEffect
import com.pass.presentation.state.screen.AnalysisLoadingState
import com.pass.presentation.state.screen.AnalysisState
import dagger.hilt.android.lifecycle.HiltViewModel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val getDiariesByMonthUseCase: GetDiariesByMonthUseCase
): ViewModel(), ContainerHost<AnalysisState, AnalysisSideEffect> {

    override val container: Container<AnalysisState, AnalysisSideEffect> = container(
        initialState = AnalysisState()
    )

    fun processIntent(intent: AnalysisIntent) = intent {
        when (intent) {
            is AnalysisIntent.LoadDiaries -> {
                loadDiaries()
            }

            is AnalysisIntent.OnDateSelected -> {
                setSelectDate(LocalDate.of(state.datePickerYear, intent.selectedMonth, 1))
            }

            is AnalysisIntent.UpdateDatePickerYear -> reduce {
                state.copy(datePickerYear = intent.year)
            }

            is AnalysisIntent.UpdateDatePickerDialog -> reduce {
                state.copy(isOpenDatePicker = intent.isOpen)
            }

            is AnalysisIntent.OnSelectPreviousMonth -> {
                val newDate = state.selectedDate.minusMonths(1)
                reduce {
                    state.copy(
                        selectedDate = newDate,
                        datePickerYear = newDate.year
                    )
                }
                loadDiaries()
            }

            is AnalysisIntent.OnSelectNextMonth -> {
                val newDate = state.selectedDate.plusMonths(1)
                if (newDate.isAfter(LocalDate.now())) {
                    postSideEffect(AnalysisSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
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
        }
    }

    private fun loadDiaries() = intent {
        reduce { state.copy(loadingState = AnalysisLoadingState.Loading) }
        try {
            val diaries = getDiariesByMonthUseCase(state.selectedDate.year.toString(), state.selectedDate.monthValue.toString())
            reduce {
                state.copy(
                    loadingState = AnalysisLoadingState.Success(
                        diaries.sortedBy { -it.day.toInt() }
                    )
                )
            }
        } catch (e: Exception) {
            reduce { state.copy(loadingState = AnalysisLoadingState.Error(e)) }
        }
    }

    private fun setSelectDate(newDate: LocalDate) = intent {
        if (newDate.isAfter(LocalDate.now())) {
            postSideEffect(AnalysisSideEffect.Toast("오지 않은 날짜는 설정할 수 없습니다."))
        } else {
            reduce {
                state.copy(
                    selectedDate = newDate,
                    datePickerYear = newDate.year
                )
            }

            // selectedDate 변경 시마다 diary 업데이트
            loadDiaries()
        }
    }
}