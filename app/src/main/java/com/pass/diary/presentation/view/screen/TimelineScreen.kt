package com.pass.diary.presentation.view.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.TimelineIntent
import com.pass.diary.presentation.state.TimelineState
import com.pass.diary.presentation.viewmodel.TimelineViewModel
import org.koin.compose.getKoin
import java.time.LocalDate

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = getKoin().get()) {
    val state by viewModel.state.collectAsState()

    val localDateTime = LocalDate.now().monthValue.toString()
    viewModel.processIntent(TimelineIntent.LoadDiaries(localDateTime))

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
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp)) {
                items(diaries) {diary ->
                    DiaryItem(diary)
                }
            }
        }
        is TimelineState.Error -> {

        }
    }
}

@Composable
fun DiaryItem(diary: Diary) {
    Text(text = diary.year + diary.month + diary.day + diary.dayOfWeek)
    Text(text = diary.content)
}