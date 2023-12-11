package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.R

@Composable
fun SettingBackup(
    loginState: Boolean,
    signIn: () -> Unit,
    signOut: () -> Unit
) {
    val buttonText = if (loginState) { "Sign out with Google" } else { "Sign in with Google" }
    val titleText = if (loginState) { "" } else { "구글 드라이브 사용을 위해 구글 로그인이 필요합니다." }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (loginState) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                SettingRow(
                    iconResource = R.drawable.ic_backup,
                    iconDescription = "백업",
                    onClick = {
                        //@TODO 백업
                    }
                )

                SettingRow(
                    iconResource = R.drawable.ic_restore,
                    iconDescription = "복원",
                    onClick = {
                        //@TODO 복원
                    }
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 10.dp).fillMaxSize(),
            verticalArrangement = if (loginState) { Arrangement.Bottom } else { Arrangement.Center }
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.body2,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            GoogleButton(
                buttonText = buttonText,
                onClick = if (loginState) { signOut } else { signIn }
            )
        }
    }
}

@Composable
fun GoogleButton(
    buttonText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth(),
        // border = BorderStroke(width = 1.dp, color = Color.LightGray),
        color = MaterialTheme.colors.surface,
        shape = MaterialTheme.shapes.medium,
        elevation = 10.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(
                start = 14.dp,
                end = 12.dp,
                top = 11.dp,
                bottom = 11.dp
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = "Google sign button",
                tint = Color.Unspecified,
                modifier = Modifier.size(35.dp)
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = buttonText,
                style = MaterialTheme.typography.overline,
                color = Color.Gray,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}