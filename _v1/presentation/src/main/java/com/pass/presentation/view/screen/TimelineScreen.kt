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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pass.domain.entity.Diary
import com.pass.presentation.intent.TimelineIntent
import com.pass.presentation.sideeffect.TimelineSideEffect
import com.pass.presentation.state.screen.TimelineLoadingState
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.activity.AddDiaryActivity
import com.pass.presentation.view.composable.CustomYearDatePicker
import com.pass.presentation.view.composable.DiaryItem
import com.pass.presentation.view.screen.Constants.INTENT_NAME_DIARY
import com.pass.presentation.viewmodel.TimelineViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.time.LocalDate

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timelineState = viewModel.collectAsState().value

    viewModel.collectSideEffect { sideEffect ->
        when(sideEffect) {
            is TimelineSideEffect.Toast -> {
                Toast.makeText(context, sideEffect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // onResume 상태 일 때 diary 업데이트
    LaunchedEffect(lifecycle) {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.processIntent(TimelineIntent.LoadDiaries)
            }
        })
    }

    TimelineScreen(
        loading = timelineState.loading,
        selectedDate = timelineState.selectedDate,
        datePickerYear = timelineState.datePickerYear,
        isOpenDatePicker = timelineState.isOpenDatePicker,
        onUpdateDatePickerDialogIsOpen = {
            viewModel.processIntent(TimelineIntent.UpdateDatePickerDialogIsOpen(it))
        },
        onStartDiaryEditActivity = { clickDiary ->
            // 다이어리 편집 액티비티 실행
            val intent = Intent(context, AddDiaryActivity::class.java)
            intent.putExtra(INTENT_NAME_DIARY, clickDiary)
            context.startActivity(intent)
        },
        onStartDiaryAddActivity = {
            // 다이어리 추가 액티비티 실행
            val intent = Intent(context, AddDiaryActivity::class.java)
            context.startActivity(intent)
        },
        onUpdateDatePickerYear = { newYear ->
            viewModel.processIntent(TimelineIntent.UpdateDatePickerYear(newYear))
        },
        onSelectNewMonth = { newMonth ->
            viewModel.processIntent(TimelineIntent.OnSelectNewMonth(newMonth))
        }
    )
}

@Composable
fun TimelineScreen(
    loading: TimelineLoadingState,
    selectedDate: LocalDate,
    datePickerYear: Int,
    isOpenDatePicker: Boolean,
    onUpdateDatePickerDialogIsOpen: (Boolean) -> Unit,
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
                is TimelineLoadingState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                    }
                }

                // 타임라인 diary 읽기 성공 상태
                is TimelineLoadingState.Success -> {
                    val diaries = loading.diaries
                    val date = selectedDate.year.toString() + "." + selectedDate.monthValue + "."

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

                            IconButton(onClick = { onUpdateDatePickerDialogIsOpen(true) }) {
                                Icon(Icons.Default.ArrowDropDown, "DatePicker Button")
                            }
                        }

                        HorizontalDivider(
                            color = LineGray,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        LazyColumn {
                            items(diaries) { diary ->
                                DiaryItem(
                                    diary = diary,
                                    onClickItem = onStartDiaryEditActivity
                                )
                            }
                        }
                    }
                }

                // 타임라인 읽기 실패 상태 : errorMessage
                is TimelineLoadingState.Error -> {
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
    }

    // DatePicker show / hide
    if (isOpenDatePicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { onUpdateDatePickerDialogIsOpen(false) }  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomYearDatePicker(
            datePickerYear = datePickerYear,
            onDatePickerYearChange = { newYear ->
                onUpdateDatePickerYear(newYear)
            },
            onDateSelected = { selectedMonth ->
                onUpdateDatePickerDialogIsOpen(false)
                onSelectNewMonth(selectedMonth)
            },
            onDismissRequest = {
                onUpdateDatePickerDialogIsOpen(false)
                onUpdateDatePickerYear(selectedDate.year)
            }
        )
    }
}