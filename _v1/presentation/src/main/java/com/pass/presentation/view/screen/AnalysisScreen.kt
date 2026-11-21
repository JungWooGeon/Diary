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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pass.presentation.intent.AnalysisIntent
import com.pass.presentation.sideeffect.AnalysisSideEffect
import com.pass.presentation.state.screen.AnalysisLoadingState
import com.pass.presentation.view.composable.CurrentMonthWithCalendar
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.HorizontalBarChartView
import com.pass.presentation.viewmodel.AnalysisViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.LocalDate

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = hiltViewModel()) {

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val analysisState = viewModel.collectAsState().value

    // onResume 상태 일 때 diary 업데이트
    LaunchedEffect(lifecycle) {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(AnalysisIntent.LoadDiaries)
            }
        })
    }

    viewModel.collectSideEffect { sideEffect ->
        when(sideEffect) {
            is AnalysisSideEffect.Toast -> {
                Toast.makeText(context, sideEffect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    AnalysisScreen(
        loading = analysisState.loadingState,
        selectedDate = analysisState.selectedDate,
        datePickerYear = analysisState.datePickerYear,
        isOpenDatePicker = analysisState.isOpenDatePicker,
        onSelectPreviousMonth = { viewModel.processIntent(AnalysisIntent.OnSelectPreviousMonth) },
        onSelectNextMonth = { viewModel.processIntent(AnalysisIntent.OnSelectNextMonth) },
        onUpdateDatePickerDialog = { viewModel.processIntent(AnalysisIntent.UpdateDatePickerDialog(it)) },
        onUpdateDatePickerYear = { newYear -> viewModel.processIntent(AnalysisIntent.UpdateDatePickerYear(newYear)) },
        onDateSelected = { selectedMonth -> viewModel.processIntent(AnalysisIntent.OnDateSelected(selectedMonth)) }
    )
}

@Composable
fun AnalysisScreen(
    loading: AnalysisLoadingState,
    selectedDate: LocalDate,
    datePickerYear: Int,
    isOpenDatePicker: Boolean,
    onSelectPreviousMonth: () -> Unit,
    onSelectNextMonth: () -> Unit,
    onUpdateDatePickerDialog: (Boolean) -> Unit,
    onUpdateDatePickerYear: (Int) -> Unit,
    onDateSelected: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 상태에 따라 자연스러운 화면 전환을 위해 Crossfase 사용
        Crossfade(targetState = loading, label = "") { loading ->
            when (loading) {
                // Loading 상태 : Indicator
                is AnalysisLoadingState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                    }
                }

                // 타임라인 diary 읽기 성공 상태
                is AnalysisLoadingState.Success -> {
                    val diaries = loading.diaries

                    Column {
                        // 날짜 표시 및 수정
                        CurrentMonthWithCalendar(
                            currentMonth = selectedDate.monthValue.toString(),
                            onSelectPreviousMonth = onSelectPreviousMonth,
                            onSelectNextMonth = onSelectNextMonth,
                            onClickSelectMonth = { onUpdateDatePickerDialog(true) }
                        )
                        HorizontalBarChartView(diaries)
                    }
                }

                // 타임라인 읽기 실패 상태 : errorMessage
                is AnalysisLoadingState.Error -> {
                    val errorMessage = loading.error.message
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
    if (isOpenDatePicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { onUpdateDatePickerDialog(false) },  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomYearDatePicker(
            datePickerYear = datePickerYear,
            onDatePickerYearChange = { newYear -> onUpdateDatePickerYear(newYear) },
            onDateSelected = { selectedMonth ->
                onUpdateDatePickerDialog(false)
                onDateSelected(selectedMonth)
            },
            onDismissRequest = {
                onUpdateDatePickerDialog(false)
                onUpdateDatePickerYear(selectedDate.year)
            }
        )
    }
}