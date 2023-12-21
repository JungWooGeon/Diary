package com.pass.presentation.view.screen

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pass.presentation.intent.AnalysisIntent
import com.pass.presentation.state.TimelineState
import com.pass.presentation.view.composable.CurrentMonthWithCalendar
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.HorizontalBarChartView
import com.pass.presentation.viewmodel.AnalysisViewModel
import org.koin.androidx.compose.getViewModel

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = getViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // 화면 로드 상태
    val state by viewModel.state.collectAsState()

    // UI 상 에서 선택된 날짜
    val selectedDateState by viewModel.selectedDateState.collectAsState()

    // Custom Date Picker 에서 선택된 연도 상태
    val datePickerYearState by viewModel.datePickerYearState.collectAsState()

    // DatePicker show / hide 상태
    val isDatePickerOpenState by viewModel.isDatePickerOpenState.collectAsState()

    // 날짜 선택 예외 처리 상태 -> true 일 경우 에러 토스트 메시지 출력
    val selectDateErrorState by viewModel.selectDateErrorState.collectAsState()
    LaunchedEffect(selectDateErrorState) {
        if (selectDateErrorState) {
            Toast.makeText(context, "오지 않은 날짜는 설정할 수 없습니다.", Toast.LENGTH_SHORT).show()
            viewModel.processIntent(AnalysisIntent.OnCompleteShowToastErrorMessage)
        }
    }

    // onResume 상태 일 때 diary 업데이트
    LaunchedEffect(lifecycle) {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(AnalysisIntent.LoadDiaries)
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 상태에 따라 자연스러운 화면 전환을 위해 Crossfase 사용
        Crossfade(targetState = state, label = "") { state ->
            when (state) {
                // Loading 상태 : Indicator
                is TimelineState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                    }
                }

                // 타임라인 diary 읽기 성공 상태
                is TimelineState.Success -> {
                    val diaries = state.diaries

                    Column {
                        // 날짜 표시 및 수정
                        CurrentMonthWithCalendar(
                            currentMonth = selectedDateState.monthValue.toString(),
                            onSelectPreviousMonth = { viewModel.processIntent(AnalysisIntent.OnSelectPreviousMonth) },
                            onSelectNextMonth = { viewModel.processIntent(AnalysisIntent.OnSelectNextMonth) },
                            onClickSelectMonth = { viewModel.processIntent(AnalysisIntent.UpdateDatePickerDialog(true)) }
                        )
                        HorizontalBarChartView(diaries)
                    }
                }

                // 타임라인 읽기 실패 상태 : errorMessage
                is TimelineState.Error -> {
                    val errorMessage = state.error.message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (errorMessage != null) {
                            Text(text = errorMessage)
                        }
                    }
                }
            }
        }
    }

    // DatePicker show / hide
    if (isDatePickerOpenState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { viewModel.processIntent(AnalysisIntent.UpdateDatePickerDialog(false)) },  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomYearDatePicker(
            datePickerYear = datePickerYearState,
            onDatePickerYearChange = { newYear -> viewModel.processIntent(AnalysisIntent.UpdateDatePickerYear(newYear)) },
            onDateSelected = { selectedMonth ->
                viewModel.processIntent(AnalysisIntent.UpdateDatePickerDialog(false))
                viewModel.processIntent(AnalysisIntent.OnDateSelected(selectedMonth))
            },
            onDismissRequest = {
                viewModel.processIntent(AnalysisIntent.UpdateDatePickerDialog(false))
                viewModel.processIntent(AnalysisIntent.UpdateDatePickerYear(selectedDateState.year))
            }
        )
    }
}