package com.pass.presentation.view.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.state.TimelineState
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.activity.AddDiaryActivity
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.DiaryItem
import com.pass.presentation.view.screen.Constants.INTENT_NAME_DIARY
import com.pass.presentation.viewmodel.TimelineViewModel

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val context = LocalContext.current

    val timeLineState by viewModel.timeLineState.collectAsState()

    // UI 상 에서 선택된 날짜 상태
    val selectedDateState by viewModel.selectedDateState.collectAsState()

    // Custom Date Picker 에서 선택된 연도 상태
    val datePickerYearState by viewModel.datePickerYearState.collectAsState()

    // DatePicker show / hide 상태
    val isDatePickerOpenState by viewModel.isDatePickerOpenState.collectAsState()

    // 날짜 선택 예외 처리 상태 -> 날짜 선택 실패 시 토스트 메시지 출력
    val selectedDateErrorState by viewModel.selectedDateErrorState.collectAsState()
    LaunchedEffect(selectedDateErrorState) {
        if (selectedDateErrorState) {
            Toast.makeText(context, "오지 않은 날짜는 설정할 수 없습니다.", Toast.LENGTH_SHORT).show()
            viewModel.processIntent(TimelineIntent.OnCompleteShowToastErrorMessage)
        }
    }

    // selectedDate 변경 시마다 diary 업데이트
    LaunchedEffect(selectedDateState) {
        viewModel.processIntent(TimelineIntent.LoadDiaries(selectedDateState.year.toString(), selectedDateState.monthValue.toString()))
    }

    // onResume 상태 일 때 diary 업데이트
    LaunchedEffect(lifecycle) {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(TimelineIntent.LoadDiaries(selectedDateState.year.toString(), selectedDateState.monthValue.toString()))
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 상태에 따라 자연스러운 화면 전환을 위해 Crossfase 사용
        Crossfade(targetState = timeLineState, label = "") { timeLineState ->
            when (timeLineState) {
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
                    val diaries = timeLineState.diaries
                    val date = selectedDateState.year.toString() + "." + selectedDateState.monthValue + "."

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                text = date
                            )

                            IconButton(onClick = {
                                viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(true))
                            }) {
                                Icon(Icons.Default.ArrowDropDown, "DatePicker Button")
                            }
                        }

                        Divider(
                            color = LineGray,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        LazyColumn {
                            items(diaries) { diary ->
                                DiaryItem(
                                    diary = diary,
                                    onClickItem = { clickDiary ->
                                        // 다이어리 편집 액티비티 실행
                                        val intent = Intent(context, AddDiaryActivity::class.java)
                                        intent.putExtra(INTENT_NAME_DIARY, clickDiary)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }

                // 타임라인 읽기 실패 상태 : errorMessage
                is TimelineState.Error -> {
                    val errorMessage = timeLineState.error.message
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
            onClick = {
                // 다이어리 추가 액티비티 실행
                val intent = Intent(context, AddDiaryActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color.Black,
            contentColor = Color.White
        ) {
            Icon(Icons.Filled.Create, contentDescription = "Add button")
        }
    }

    // DatePicker show / hide
    if (isDatePickerOpenState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(false)) }  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomYearDatePicker(
            datePickerYear = datePickerYearState,
            onDatePickerYearChange = { newYear ->
                viewModel.processIntent(TimelineIntent.UpdateDatePickerYear(newYear))
            },
            onDateSelected = { selectedMonth ->
                viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(false))
                viewModel.processIntent(TimelineIntent.OnDateSelected(selectedMonth))
            },
            onDismissRequest = {
                viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(false))
                viewModel.processIntent(TimelineIntent.UpdateDatePickerYear(selectedDateState.year))
            }
        )
    }
}