package com.pass.presentation.view.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pass.domain.entity.Diary
import com.pass.presentation.intent.CalendarIntent
import com.pass.presentation.sideeffect.CalendarSideEffect
import com.pass.presentation.state.screen.CalendarLoadingState
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.activity.AddDiaryActivity
import com.pass.presentation.view.composable.CalendarMonthGrid
import com.pass.presentation.view.composable.CalendarWeekdaysTitle
import com.pass.presentation.view.composable.CurrentMonthWithCalendar
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.DiaryItem
import com.pass.presentation.viewmodel.CalendarViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.LocalDate

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val calendarState = viewModel.collectAsState().value

    viewModel.collectSideEffect { sideEffect ->
        when(sideEffect) {
            is CalendarSideEffect.Toast -> {
                Toast.makeText(context, sideEffect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // onResume 상태 일 때 diary 업데이트
    LaunchedEffect(lifecycle) {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(CalendarIntent.LoadDiaries)
            }
        })
    }

    CalendarScreen(
        loading = calendarState.loading,
        selectedDate = calendarState.selectedDate,
        datePickerYear = calendarState.datePickerYear,
        isOpenDatePicker = calendarState.isOpenDatePicker,
        onSelectPreviousMonth = { dayOfMonth -> viewModel.processIntent(CalendarIntent.OnSelectPreviousMonth(dayOfMonth)) },
        onSelectNextMonth = { dayOfMonth -> viewModel.processIntent(CalendarIntent.OnSelectNextMonth(dayOfMonth)) },
        onSelectTheCurrentMonthDate = { dayOfMonth -> viewModel.processIntent(CalendarIntent.OnSelectTheCurrentMonthDate(dayOfMonth)) },
        onUpdateDatePickerIsOpen = { viewModel.processIntent(CalendarIntent.UpdateDatePickerIsOpen(it)) },
        onStartDiaryEditActivity = { clickDiary ->
            // 다이어리 편집 액티비티 실행
            val intent = Intent(context, AddDiaryActivity::class.java)
            intent.putExtra(Constants.INTENT_NAME_DIARY, clickDiary)
            context.startActivity(intent)
        },
        onStartDiaryAddActivity = {
            // 다이어리 추가 액티비티 실행
            val intent = Intent(context, AddDiaryActivity::class.java)
            context.startActivity(intent)
        },
        onUpdateDatePickerYear = { newYear ->
            viewModel.processIntent(CalendarIntent.UpdateDatePickerYear(newYear))
        },
        onSelectNewMonth = { newMonth ->
            viewModel.processIntent(CalendarIntent.OnSelectNewMonth(newMonth))
        }
    )
}

@Composable
fun CalendarScreen(
    loading: CalendarLoadingState,
    selectedDate: LocalDate,
    datePickerYear: Int,
    isOpenDatePicker: Boolean,
    onSelectPreviousMonth: (Int) -> Unit,
    onSelectNextMonth: (Int) -> Unit,
    onSelectTheCurrentMonthDate: (Int) -> Unit,
    onUpdateDatePickerIsOpen: (Boolean) -> Unit,
    onStartDiaryEditActivity: (Diary) -> Unit,
    onStartDiaryAddActivity: () -> Unit,
    onUpdateDatePickerYear: (Int) -> Unit,
    onSelectNewMonth: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 상태에 따라 자연스러운 화면 전환을 위해 Crossfase 사용
        Crossfade(targetState = loading, label = "") { loading ->
            when (loading) {
                // Loading 상태 : Indicator
                is CalendarLoadingState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                    }
                }

                // diary 읽기 성공 상태
                is CalendarLoadingState.Success -> {
                    val diaries = loading.diaries

                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        // 날짜 표시 및 수정
                        CurrentMonthWithCalendar(
                            currentMonth = selectedDate.monthValue.toString(),
                            onSelectPreviousMonth = { onSelectPreviousMonth(selectedDate.dayOfMonth) },
                            onSelectNextMonth = { onSelectNextMonth(selectedDate.dayOfMonth) },
                            onClickSelectMonth = { onUpdateDatePickerIsOpen(true) }
                        )

                        // 달력 표시
                        CalendarWeekdaysTitle()
                        CalendarMonthGrid(
                            year = selectedDate.year,
                            month = selectedDate.monthValue,
                            dayOfMonth = selectedDate.dayOfMonth,
                            diaries = diaries,
                            onClickTheCurrentMonthDate = { date -> onSelectTheCurrentMonthDate(date) },
                            onClickThePreviousMonthDate = { date -> onSelectPreviousMonth(date) },
                            onClickTheNextMonthDate = { date -> onSelectNextMonth(date) }
                        )

                        HorizontalDivider(
                            color = LineGray,
                            thickness = 1.dp,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .padding(horizontal = 10.dp)
                        )

                        diaries.forEach { diary ->
                            if (diary.day.toInt() == selectedDate.dayOfMonth) {
                                DiaryItem(
                                    diary = diary,
                                    onClickItem = onStartDiaryEditActivity
                                )
                            }
                        }
                    }
                }

                // 읽기 실패 상태 : errorMessage
                is CalendarLoadingState.Error -> {
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

        // "일기 추가" 화면 이동 버튼
        FloatingActionButton(
            onClick = onStartDiaryAddActivity,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Icon(Icons.Filled.Create, contentDescription = "Add button")
        }

        // DatePicker show / hide
        if (isOpenDatePicker) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                    .clickable { onUpdateDatePickerIsOpen(false) },  // 배경을 클릭하면 다이얼로그를 닫음
            )

            CustomYearDatePicker(
                datePickerYear = datePickerYear,
                onDatePickerYearChange = onUpdateDatePickerYear,
                onDateSelected = { selectedMonth ->
                    onUpdateDatePickerIsOpen(false)
                    onSelectNewMonth(selectedMonth)
                },
                onDismissRequest = {
                    onUpdateDatePickerIsOpen(false)
                    onUpdateDatePickerYear(selectedDate.year)
                }
            )
        }
    }
}