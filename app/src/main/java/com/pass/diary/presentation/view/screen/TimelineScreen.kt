package com.pass.diary.presentation.view.screen

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.R
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
import com.pass.diary.presentation.ui.theme.LineGray
import com.pass.diary.presentation.view.activity.AddDiaryActivity
import com.pass.diary.presentation.viewmodel.TimelineViewModel
import org.koin.androidx.compose.getViewModel
import java.time.LocalDate

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = getViewModel()) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    // selectedDate 변경 시마다 diary 업데이트
    LaunchedEffect(selectedDate) {
        viewModel.processIntent(TimelineIntent.LoadDiaries(selectedDate.monthValue.toString()))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is TimelineState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                }
            }

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

                        IconButton(onClick = {
                            showDatePickerDialog(context, viewModel, selectedDate)
                        }) {
                            Icon(Icons.Default.ArrowDropDown, "Add Button")
                        }
                    }

                    Divider(
                        color = LineGray,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    LazyColumn() {
                        items(diaries) { diary ->
                            DiaryItem(diary)
                        }
                    }
                }

            }

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
}

@Composable
fun DiaryItem(diary: Diary) {
    val diaryDate = diary.year + "년 " + diary.month + "월 " + diary.day + "일 " + diary.dayOfWeek + "요일"

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

fun showDatePickerDialog(context: Context, viewModel: TimelineViewModel, selectedDate: LocalDate) {
    DatePickerDialog(context, { _, year, month, dayOfMonth ->
        // 사용자가 날짜를 선택하면 selectedDate 업데이트
        viewModel.processIntent(TimelineIntent.UpdateMonth(LocalDate.of(year, month + 1, dayOfMonth)))
    }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
}