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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.state.TimelineState
import com.pass.presentation.view.composable.CurrentMonthWithCalendar
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.HorizontalBarChartView
import com.pass.presentation.viewmodel.TimelineViewModel
import org.koin.androidx.compose.getViewModel
import java.time.LocalDate

@Composable
fun AnalysisScreen(viewModel: TimelineViewModel = getViewModel()) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsState()

    // UI 상 에서 선택된 날짜
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // Custom Date Picker 에서 선택된 연도
    var datePickerYear by remember { mutableIntStateOf(selectedDate.year) }

    // DatePicker show / hide 상태
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // selectedDate 변경 시마다 diary 업데이트
    LaunchedEffect(selectedDate) {
        viewModel.processIntent(
            TimelineIntent.LoadDiaries(
                selectedDate.year.toString(),
                selectedDate.monthValue.toString()
            )
        )
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
                            currentMonth = selectedDate.monthValue.toString(),
                            onSelectPreviousMonth = {
                                selectedDate = selectedDate.minusMonths(1)
                            },
                            onSelectNextMonth = {
                                val newDate = selectedDate.plusMonths(1)
                                if (newDate.isAfter(LocalDate.now())) {
                                    Toast.makeText(
                                        context,
                                        "오지 않은 날짜는 설정할 수 없습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    selectedDate = newDate
                                }
                            },
                            onClickSelectMonth = {
                                isDatePickerOpen = true
                            }
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
    if (isDatePickerOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { isDatePickerOpen = false },  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomYearDatePicker(
            datePickerYear = datePickerYear,
            onDatePickerYearChange = { newYear ->
                datePickerYear = newYear
            },
            onDateSelected = { selectedMonth ->
                isDatePickerOpen = false
                val newDate = LocalDate.of(datePickerYear, selectedMonth, 1)
                if (newDate.isAfter(LocalDate.now())) {
                    Toast.makeText(context, "오지 않은 날짜는 설정할 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    selectedDate = newDate
                }
            },
            onDismissRequest = {
                isDatePickerOpen = false
                datePickerYear = selectedDate.year
            }
        )
    }
}