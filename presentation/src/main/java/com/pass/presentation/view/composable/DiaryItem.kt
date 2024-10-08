package com.pass.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.domain.entity.Diary
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.screen.Constants

@Composable
fun DiaryItem(
    diary: Diary,
    onClickItem: (diary: Diary) -> Unit
) {
    val diaryDate =
        diary.year + "년 " + diary.month + "월 " + diary.day + "일 " + diary.dayOfWeek + "요일"

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
            .clickable {
                onClickItem(diary)
            }
    ) {
        if (diary.emoticonId1 == null) {
            Image(
                painter = painterResource(id = Constants.EMOTICON_RAW_ID_LIST[0]),
                contentDescription = "기본 이모지",
                modifier = Modifier.size(50.dp)
            )
        } else {
            Image(
                painter = painterResource(id = diary.emoticonId1!!),
                contentDescription = "이모지",
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.size(30.dp))

        Column {
            Text(
                text = diaryDate,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.size(10.dp))

            Text(
                text = diary.title,
                fontSize = 14.sp
            )
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(top = 20.dp),
        thickness = 1.dp,
        color = LineGray
    )
}