package com.pass.presentation.view.composable

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    isLoggedIn: Boolean,
    signIn: () -> Unit,
    signOut: () -> Unit,
    backUp: () -> Unit,
    restore: () -> Unit
) {
    val buttonText = if (isLoggedIn) { "Sign out with Google" } else { "Sign in with Google" }
    val titleText = if (isLoggedIn) { "" } else { "구글 드라이브 사용을 위해 구글 로그인이 필요합니다." }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (isLoggedIn) {
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                SettingRow(
                    iconResource = R.drawable.ic_backup,
                    iconDescription = "백업",
                    onClick = { backUp() }
                )

                SettingRow(
                    iconResource = R.drawable.ic_restore,
                    iconDescription = "복원",
                    onClick = { restore() }
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 10.dp).fillMaxSize(),
            verticalArrangement = if (isLoggedIn) { Arrangement.Bottom } else { Arrangement.Center }
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            GoogleButton(
                buttonText = buttonText,
                onClick = if (isLoggedIn) { signOut } else { signIn }
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
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 10.dp
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
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}