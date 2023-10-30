package com.pass.diary.presentation.view.screen

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pass.diary.R
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
import com.pass.diary.presentation.ui.theme.LineGray
import com.pass.diary.presentation.view.activity.AddDiaryActivity
import com.pass.diary.presentation.view.screen.Constants.INTENT_NAME_DATE
import com.pass.diary.presentation.viewmodel.TimelineViewModel
import org.koin.androidx.compose.getViewModel
import java.time.LocalDate

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = getViewModel()) {
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
        viewModel.processIntent(TimelineIntent.LoadDiaries(selectedDate.monthValue.toString()))
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                val diaries = remember { (state as TimelineState.Success).diaries }
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

                        IconButton(onClick = { isDatePickerOpen = true }) {
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
                            DiaryItem(diary)
                        }
                    }
                }

            }

            // 타임라인 읽기 실패 상태 : errorMessage
            is TimelineState.Error -> {
                val errorMessage = (state as TimelineState.Error).error.message
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

        // "일기 추가" 화면 이동 버튼
        FloatingActionButton(
            onClick = {
                // 다이어리 추가 액티비티 실행
                val intent = Intent(context, AddDiaryActivity::class.java)
                val date = LocalDate.now().year.toString() + "." + LocalDate.now().monthValue + "." + LocalDate.now().dayOfMonth
                intent.putExtra(INTENT_NAME_DATE, date)
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
    if (isDatePickerOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { isDatePickerOpen = false },  // 배경을 클릭하면 다이얼로그를 닫음
        )

        CustomDatePicker(
            datePickerYear = datePickerYear,
            onDatePickerYearChange = { newYear ->
                datePickerYear = newYear
            },
            onDateSelected = { selectedMonth ->
                isDatePickerOpen = false
                selectedDate = LocalDate.of(datePickerYear, selectedMonth, 1)
            },
            onDismissRequest = {
                isDatePickerOpen = false
                datePickerYear = selectedDate.year
            }
        )
    }
}

@Composable
fun DiaryItem(diary: Diary) {
    val diaryDate =
        diary.year + "년 " + diary.month + "월 " + diary.day + "일 " + diary.dayOfWeek + "요일"

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        if (diary.emoticonId1 == null) {
            Image(
                painter = painterResource(id = R.drawable.em_smile),
                contentDescription = "기본 이모지",
                modifier = Modifier.size(50.dp)
            )
        } else {
            Image(
                painter = painterResource(id = diary.emoticonId1),
                contentDescription = "이모지",
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.size(30.dp))

        Column {
            Text(
                text = diaryDate,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.size(10.dp))

            Text(
                text = diary.content,
                fontSize = 10.sp
            )
        }
    }

    Divider(
        color = LineGray,
        thickness = 1.dp,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Composable
fun CustomDatePicker(
    datePickerYear: Int,
    onDatePickerYearChange: (newYear: Int) -> Unit,
    onDateSelected: (selectedMonth: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp)) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .wrapContentSize()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "left button",
                            modifier = Modifier.clickable { onDatePickerYearChange(datePickerYear - 1) }
                        )
                        Text(text = datePickerYear.toString(), fontSize = 20.sp)
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "right button",
                            modifier = Modifier.clickable { onDatePickerYearChange(datePickerYear + 1) }
                        )
                    }

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(1, 2, 3, 4), onDateSelected)

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(5, 6, 7, 8), onDateSelected)

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(9, 10, 11, 12), onDateSelected)
                }
            }
        }
    }
}

@Composable
fun DatePickerRow(dates: List<Int>, onDateSelected: (selectedDate: Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        dates.forEach { date ->
            Box(modifier = Modifier
                .size(20.dp)
                .clickable { onDateSelected(date) }) {
                Text(text = date.toString(), fontSize = 15.sp)
            }
        }
    }
}