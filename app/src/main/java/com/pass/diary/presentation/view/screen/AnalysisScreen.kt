package com.pass.diary.presentation.view.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.pass.diary.R
import com.pass.diary.presentation.ui.theme.DiaryTheme


@Composable
fun AnalysisScreen() {
    Column {
        GraphWithEmojis()
    }
}

@Composable
fun GraphWithEmojis() {
    val emojis = listOf(R.raw.em_happy, R.raw.em_like, R.raw.em_angry)
    val emojiCounts = listOf(1.0F, 2.0F, 3.0F) // 각 이모티콘의 횟수

    val values = ArrayList<BarData>()
    for (i in 0 until 3) {
        val barData = BarData(BarDataSet(listOf(BarEntry(i.toFloat(), emojiCounts[i])), emojiCounts[i].toString())).apply {
            this.barWidth = 0.2F
        }
        values.add(barData)
    }

    Column (
        modifier = Modifier
            .fillMaxHeight()
            .padding(10.dp)
    ) {
        emojis.forEachIndexed { index, emoji ->
            Row (
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
                        HorizontalBarChart(context).apply {
                            this.data = values[index]
                            setExtraOffsets(0f, 0f, 0f, 0f) // 차트의 여백 제거
                            setTouchEnabled(false)
                            description.isEnabled = false
                            xAxis.isEnabled = false
                            axisLeft.isEnabled = false
                            axisLeft.axisMinimum = 0f
                            axisLeft.axisMaximum = emojiCounts.maxOrNull()!!
                            axisRight.axisMinimum = 0f
                            axisRight.axisMaximum = emojiCounts.maxOrNull()!!
                            setFitBars(true)
                            animateY(1500)
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewAnalysisScreen() {
    DiaryTheme {
        AnalysisScreen()
    }
}