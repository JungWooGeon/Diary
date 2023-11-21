package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.R
import com.pass.diary.presentation.state.SettingState
import com.pass.diary.presentation.ui.theme.LineGray

@Composable
fun SettingDefault(
    onClick: (SettingState) -> Unit
) {
    SettingRow(
        iconResource = R.drawable.ic_font,
        iconDescription = "폰트",
        onClick = { onClick(SettingState.FontSetting) }
    )

    SettingRow(
        iconResource = R.drawable.ic_theme,
        iconDescription = "테마",
        onClick = {
            //@TODO 테마 설정 화면 전환
        }
    )

    SettingRow(
        iconResource = R.drawable.ic_lock,
        iconDescription = "화면 잠금",
        onClick = {
            //@TODO 화면 잠금 설정 화면 전환
        }
    )

    SettingRow(
        iconResource = R.drawable.ic_calendar_today,
        iconDescription = "시작 요일 설정",
        onClick = {
            //@TODO 시작 요일 설정 화면 전환
        }
    )

    SettingRow(
        iconResource = R.drawable.ic_theme,
        iconDescription = "백업 / 복원",
        onClick = {
            //@TODO 백업 / 복원 설정 화면 전환
        }
    )

    Divider(
        color = LineGray,
        thickness = 1.dp,
        modifier = Modifier.padding(top = 30.dp)
    )

    Text(
        text = "개인정보처리방침",
        fontSize = 16.sp,
        modifier = Modifier
            .padding(start = 20.dp, top = 30.dp)
            .fillMaxWidth()
    )

    Text(
        text = "라이센스",
        fontSize = 16.sp,
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp)
            .fillMaxWidth()
    )

    Text(
        text = "평가하러 가기",
        fontSize = 16.sp,
        modifier = Modifier
            .padding(start = 20.dp, top = 20.dp)
            .fillMaxWidth()
    )
}

@Composable
fun SettingRow(
    iconResource: Int,
    iconDescription: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 30.dp, start = 20.dp)
            .clickable {
                onClick()
            },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconResource), contentDescription = iconDescription)
        Text(
            text = iconDescription,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}