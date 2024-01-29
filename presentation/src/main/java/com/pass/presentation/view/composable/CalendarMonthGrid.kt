package com.pass.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pass.domain.entity.Diary
import com.pass.presentation.ui.theme.Fluorescent
import java.time.YearMonth

@Composable
fun CalendarMonthGrid(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    diaries: List<Diary>,
    onClickTheCurrentMonthDate: (dayOfMonth: Int) -> Unit,
    onClickThePreviousMonthDate: (dayOfMonth: Int) -> Unit,
    onClickTheNextMonthDate: (dayOfMonth: Int) -> Unit
) {
    val currentMonth = YearMonth.of(year, month)
    val previousMonth = currentMonth.minusMonths(1)

    val daysInMonth = currentMonth.lengthOfMonth()
    val daysInPreviousMonth = previousMonth.lengthOfMonth()

    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7
    val totalDays = (firstDayOfWeek + daysInMonth + 6) / 7 * 7

    Column(modifier = Modifier.padding(10.dp)) {
        for (week in 0 until totalDays / 7) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (day in 0 until 7) {
                    val index = week * 7 + day

                    // 현재 '월' 인지 확인
                    val isCurrentMonth =
                        index >= firstDayOfWeek && index < firstDayOfWeek + daysInMonth

                    // 현재 '일' 확인
                    val date = when {
                        index < firstDayOfWeek -> daysInPreviousMonth - (firstDayOfWeek - index - 1)
                        isCurrentMonth -> index - firstDayOfWeek + 1
                        else -> index - firstDayOfWeek - daysInMonth + 1
                    }

                    // 현재 날짜의 '일기' 확인
                    val diaryForTheDay = if (isCurrentMonth) {
                        diaries.find { it.year.toInt() == year && it.month.toInt() == month && it.day.toInt() == date }
                    } else null

                    // 현재 날짜 확인
                    val isToday = (isCurrentMonth && date == dayOfMonth)

                    // Box 형태로 현재 날짜 표시 (이모티콘은 있다면 표시)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .border(
                                width = 2.dp,
                                color = if (isToday) Fluorescent else Color.Transparent,
                                shape = RoundedCornerShape(10)
                            )
                            .clickable {
                                if (index < firstDayOfWeek) {
                                    // 이전 '월'에서 클릭 시
                                    onClickThePreviousMonthDate(date)
                                } else if (index >= firstDayOfWeek + daysInMonth) {
                                    // 다음 '월'에서 클릭 시
                                    onClickTheNextMonthDate(date)
                                } else {
                                    // 현재 '월'에서 클릭 시
                                    onClickTheCurrentMonthDate(date)
                                }
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(5.dp)
                        ) {
                            // 날짜(일) 표시
                            Text(
                                text = "$date",
                                color = if (isCurrentMonth) Color.Black else Color.Gray
                            )

                            // 이미지 있는지 확인 후 표시 (없다면 빈 공간 표시)
                            val imageId = diaryForTheDay?.emoticonId1
                            if (imageId != null) {
                                Image(
                                    painter = painterResource(id = imageId),
                                    contentDescription = "이모티콘",
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(20.dp)
                                )
                            } else {
                                Spacer(
                                    Modifier
                                        .padding(10.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}