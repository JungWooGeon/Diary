package com.pass.diary.presentation.view.screen

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import com.pass.diary.BuildConfig
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

    val context = LocalContext.current

    val loginState by viewModel.loginState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.login(
            activityResultData = it.data,
            onSuccess = {
                Toast.makeText(context, "로그인이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            },
            onFail = {
                Toast.makeText(context, "로그인에 실패하였습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    var drivePendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val driveLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        drivePendingAction = if (result.resultCode == Activity.RESULT_OK && drivePendingAction != null) {
            // 권한 요청이 승인되었을 때 대기 중인 작업을 실행
            drivePendingAction?.invoke()
            null
        } else {
            // 권한 요청이 거부되었을 때의 처리 코드
            Toast.makeText(context, "권한 요청이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    val error by viewModel.error.collectAsState()

    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect

        Log.d("오류", "권한 요청 해보자")
        drivePendingAction = { viewModel.backup() }
        // 사용자에게 권한 부여 요청
        driveLauncher.launch(error)
    }


    val token = BuildConfig.default_web_client_id

    // 로그인 화면 요청
    val googleSignInOptions = GoogleSignInOptions
        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(token)
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (screenState != SettingState.DefaultSetting) {
                IconButton(
                    onClick = {
                        screenState = SettingState.DefaultSetting
                    },
                    modifier = Modifier
                        .weight(0.2f)
                        .size(20.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "이전 화면")
                }
                Spacer(modifier = Modifier.weight(0.8f))
            } else {
                Spacer(
                    Modifier
                        .weight(1f)
                        .size(20.dp))
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
                SettingBackup(
                    loginState = loginState,
                    signIn = {
                        launcher.launch(googleSignInClient.signInIntent)
                    },
                    signOut = {
                        viewModel.logout()
                        googleSignInClient.signOut().addOnCompleteListener {
                            Toast.makeText(context, "로그아웃이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    backUp = {
                        try {
                            viewModel.backup()
                        } catch (e: UserRecoverableAuthException) {
                            Log.d("테스트", "로그인 권한 요청 거부")
                            // 백업 작업을 대기 상태로 설정
                            drivePendingAction = { viewModel.backup() }
                            // 사용자에게 권한 부여 요청
                            driveLauncher.launch(e.intent)
                        }
                    },
                    restore = {
                        try {
                            viewModel.restore()
                        } catch (e: UserRecoverableAuthIOException) {
                            // 복원 작업을 대기 상태로 설정
                            drivePendingAction = { viewModel.restore() }
                            // 사용자에게 권한 부여 요청
                            driveLauncher.launch(e.intent)
                        }
                    }
                )
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