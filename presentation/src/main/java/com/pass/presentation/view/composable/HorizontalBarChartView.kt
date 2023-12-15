package com.pass.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.pass.domain.model.Diary
import com.pass.presentation.view.screen.Constants

@Composable
fun HorizontalBarChartView(diaries: List<Diary>) {
    val emojiCounts = MutableList(Constants.EMOTICON_RAW_ID_LIST.size) { 0 } // 각 이모티콘의 횟수를 저장할 리스트. 초기값은 0

    diaries.forEach { diary ->
        diary.emoticonId1?.let { emojiCounts[Constants.EMOTICON_RAW_ID_LIST.indexOf(it)]++ } // 해당 이모티콘의 횟수 증가
        diary.emoticonId2?.let { emojiCounts[Constants.EMOTICON_RAW_ID_LIST.indexOf(it)]++ } // 해당 이모티콘의 횟수 증가
        diary.emoticonId3?.let { emojiCounts[Constants.EMOTICON_RAW_ID_LIST.indexOf(it)]++ } // 해당 이모티콘의 횟수 증가
    }

    val resultEmojis = mutableListOf<Int>()
    val resultEmojiCounts = mutableListOf<Int>()

    emojiCounts.forEachIndexed { index, count ->
        if (count > 0) { // 횟수가 0보다 큰 이모티콘만 결과에 추가
            resultEmojis.add(Constants.EMOTICON_RAW_ID_LIST[index])
            resultEmojiCounts.add(count)
        }
    }

    val values = ArrayList<BarData>()
    for (i in 0 until resultEmojis.size) {
        val barData = BarData(
            BarDataSet(
                listOf(BarEntry(i.toFloat(), resultEmojiCounts[i].toFloat())),
                resultEmojiCounts[i].toString() + "번"
            )
        ).apply {
            this.barWidth = 0.2F
            this.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString() // 실수를 정수로 변환
                }
            })
        }
        values.add(barData)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .padding(10.dp)
    ) {
        items(resultEmojis.size) { index ->
            val emoji = resultEmojis[index]

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = emoji),
                    contentDescription = "",
                    modifier = Modifier.size(20.dp)
                )
                AndroidView(
                    modifier = Modifier
                        .weight(1f),
                    factory = { context ->
                        com.github.mikephil.charting.charts.HorizontalBarChart(context).apply {
                            this.data = values[index]
                            setExtraOffsets(0f, 0f, 0f, 0f) // 차트의 여백 제거
                            setTouchEnabled(false)
                            description.isEnabled = false
                            xAxis.setDrawLabels(false) // X축 라벨 제거
                            axisLeft.setDrawLabels(false) // 왼쪽 Y축 라벨 제거
                            axisRight.setDrawLabels(false)
                            xAxis.isEnabled = false
                            axisLeft.isEnabled = false
                            axisLeft.axisMinimum = 0f
                            axisLeft.axisMaximum = resultEmojiCounts.maxOrNull()!!.toFloat()
                            axisRight.axisMinimum = 0f
                            axisRight.axisMaximum = resultEmojiCounts.maxOrNull()!!.toFloat()
                            setFitBars(true)
                            animateY(1500)
                        }
                    }
                )
            }
        }
    }
}