package com.pass.diary.presentation.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.presentation.intent.SettingsIntent
import com.pass.diary.presentation.state.SettingState
import com.pass.diary.presentation.ui.theme.LineGray
import com.pass.diary.presentation.view.composable.SettingBackup
import com.pass.diary.presentation.view.composable.SettingDefault
import com.pass.diary.presentation.view.composable.SettingFont
import com.pass.diary.presentation.viewmodel.SettingsViewModel
import org.koin.androidx.compose.getViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = getViewModel()) {
    var screenState by remember { mutableStateOf<SettingState>(SettingState.DefaultSetting) }

    val textSize by viewModel.textSize.collectAsState()
    val textFont by viewModel.textFont.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row (
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ){
                if (screenState != SettingState.DefaultSetting) {
                    IconButton(
                        onClick = {
                            screenState = SettingState.DefaultSetting
                        },
                        modifier = Modifier.weight(0.2f)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "이전 화면")
                    }
                    Spacer(modifier = Modifier.weight(0.8f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = "설정",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.weight(1f))
            }

            Divider(
                color = LineGray,
                thickness = 1.dp,
                modifier = Modifier.padding(top = 10.dp)
            )

            when (screenState) {
                SettingState.DefaultSetting -> {
                    SettingDefault { screenState = it }
                }

                SettingState.FontSetting -> {
                    SettingFont(
                        textSize = textSize,
                        textFont = textFont,
                        onChangeFinishTextSize = {
                            viewModel.processIntent(SettingsIntent.UpdateCurrentTextSize(it))
                        },
                        onChangeFont = {
                            viewModel.processIntent(SettingsIntent.UpdateCurrentFont(it))
                        }
                    )
                }

                SettingState.BackupSetting -> {
                    SettingBackup()
                }

                SettingState.LicenseSetting -> {

                }

                SettingState.NotificationSetting -> {

                }

                SettingState.PrivacyPolicySetting -> {

                }

                SettingState.ScreenLockSetting -> {

                }

                SettingState.StartDateSetting -> {

                }

                SettingState.ThemeSetting -> {

                }
            }
        }
    }
}