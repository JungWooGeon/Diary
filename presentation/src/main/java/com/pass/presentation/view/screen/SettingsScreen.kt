package com.pass.presentation.view.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.pass.diary.BuildConfig
import com.pass.presentation.intent.SettingsIntent
import com.pass.presentation.state.LoginState
import com.pass.presentation.state.SettingState
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.composable.SettingBackup
import com.pass.presentation.view.composable.SettingDefault
import com.pass.presentation.view.composable.SettingFont
import com.pass.presentation.view.composable.SettingLicense
import com.pass.presentation.viewmodel.SettingsViewModel


@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current

    // 설정 화면 상태 (기본, 폰트, 백업 등)
    val settingsScreenState by viewModel.settingsScreenState.collectAsState()

    // 백업 확인 다이얼로그 상태
    val showConfirmBackUpDialogIsOpen by viewModel.showConfirmBackUpDialogIsOpen.collectAsState()

    // 백업 로딩 상태
    val backUpLoadingState by viewModel.backUpLoadingState.collectAsState()

    // 일기 백업 / 복원 작업 상태
    val backupDiariesState by viewModel.backupDiariesState.collectAsState()
    val restoreDiariesState by viewModel.restoreDiariesState.collectAsState()

    // 텍스트 크기, 폰트 상태
    val textSize by viewModel.textSize.collectAsState()
    val textFont by viewModel.textFont.collectAsState()
    
    // 로그인 상태
    val loginState by viewModel.loginState.collectAsState()

    // 백업 작업 완료 시 토스트 메시지 출력 + LoadingIndicator 삭제
    LaunchedEffect(backupDiariesState) {
        if (backupDiariesState == null) return@LaunchedEffect
        Toast.makeText(context, if (backupDiariesState == true) { "백업이 완료되었습니다." } else { "백업이 실패하였습니다." } , Toast.LENGTH_SHORT).show()
        viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))

        // 토스트 메시지 반복 출력 방지를 위해 백업 state null 설정
        viewModel.processIntent(SettingsIntent.SetNullState(SettingsIntent.Backup))
    }

    // 복원 작업 완료 시 토스트 메시지 출력 + LoadingIndicator 삭제
    LaunchedEffect(restoreDiariesState) {
        if (restoreDiariesState == null) return@LaunchedEffect
        Toast.makeText(context, if (restoreDiariesState == true) { "복원이 완료되었습니다." } else { "복원이 실패하였습니다." } , Toast.LENGTH_SHORT).show()
        viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))

        // 토스트 메시지 반복 출력 방지를 위해 복원 state null 설정
        viewModel.processIntent(SettingsIntent.SetNullState(SettingsIntent.Restore))
    }

    // 로그인 작업 완료시 로그인 여부에 따라 토스트 메시지 출력 + 토스트 메시지 출력 상태 반영
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            // 토스트 메시지 반복 출력 방지를 위해 LoginState SuccessIdle 로 변경
            Toast.makeText(context, "로그인이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            viewModel.processIntent(SettingsIntent.SetLoginState(LoginState.SuccessIdle))
        } else if (loginState is LoginState.Fail) {
            // 토스트 메시지 반복 출력 방지를 위해 LoginState FailIdle 로 변경
            Toast.makeText(context, "로그인에 실패하였습니다." , Toast.LENGTH_SHORT).show()
            viewModel.processIntent(SettingsIntent.SetLoginState(LoginState.FailIdle))
        }

        viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))
    }

    // 권한 허용 완료 후 로그인 창 띄우기
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.processIntent(SettingsIntent.Login(activityResultData = it.data))
    }

    // 구글 드라이브 권한 허용에 따라서 다시 실행할 작업 (백업 / 복원) 상태
    val drivePendingActionState by viewModel.drivePendingActionState.collectAsState()

    // 구글 드라이브 권한 허용 요청 launcher
    val driveLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && drivePendingActionState != null) {
            // 권한 요청이 승인되었을 때 대기 중인 작업을 실행
            drivePendingActionState?.invoke()
        } else {
            // 권한 요청이 거부되었을 때 토스트 메시지 출력
            Toast.makeText(context, "권한 요청이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))
        }

        // 작업 완료 후 권한 허용 다시 실행 작업 null 초기화 + error 상태 null 초기화
        viewModel.processIntent(SettingsIntent.SetNullDrivePendingActionAndErrorState)
    }

    // 권한이 필요할 시 error 상태
    val error by viewModel.error.collectAsState()
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect

        // 사용자에게 권한 부여 요청
        driveLauncher.launch(error!!)
    }

    // 로그인 화면 요청
    val googleSignInOptions = GoogleSignInOptions
        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(BuildConfig.default_web_client_id)
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
            if (settingsScreenState != SettingState.DefaultSetting) {
                IconButton(
                    onClick = {
                        viewModel.processIntent(SettingsIntent.SetSettingsScreenState(SettingState.DefaultSetting))
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

        when (settingsScreenState) {
            SettingState.DefaultSetting -> {
                SettingDefault(
                    onClick = { viewModel.processIntent(SettingsIntent.SetSettingsScreenState(it)) },
                    onClickPrivacyPolicy = {
                        val url = "https://ai-diary-privacy-policy.co.kr/"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        context.startActivity(intent)
                    },
                    onClickReview = {
                        val uri = Uri.parse("market://details?id=" + context.packageName)
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
            }

            SettingState.FontSetting -> {
                SettingFont(
                    textSize = textSize,
                    textFont = textFont,
                    onChangeFinishTextSize = { viewModel.processIntent(SettingsIntent.UpdateCurrentTextSize(it)) },
                    onChangeFont = { viewModel.processIntent(SettingsIntent.UpdateCurrentFont(it)) }
                )
            }

            SettingState.BackupSetting -> {
                if (backUpLoadingState) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "작업 중 화면을 이동하지 마세요.",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
                        }
                    }
                } else {
                    SettingBackup(
                        loginState = loginState,
                        signIn = {
                            viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(true))
                            launcher.launch(googleSignInClient.signInIntent)
                        },
                        signOut = {
                            viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(true))
                            viewModel.processIntent(SettingsIntent.Logout)
                            googleSignInClient.signOut().addOnCompleteListener {
                                viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))
                                Toast.makeText(context, "로그아웃이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        backUp = { viewModel.processIntent(SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen(true)) },
                        restore = { viewModel.processIntent(SettingsIntent.Restore) }
                    )
                }
            }

            SettingState.LicenseSetting -> {
                SettingLicense(onClickUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(url)
                    context.startActivity(intent)
                })
            }

            SettingState.NotificationSetting -> {
                Toast.makeText(context, "업데이트 예정입니다." , Toast.LENGTH_SHORT).show()
                viewModel.processIntent(SettingsIntent.SetSettingsScreenState(SettingState.DefaultSetting))
            }

            SettingState.ScreenLockSetting -> {
                Toast.makeText(context, "업데이트 예정입니다." , Toast.LENGTH_SHORT).show()
                viewModel.processIntent(SettingsIntent.SetSettingsScreenState(SettingState.DefaultSetting))
            }

            SettingState.StartDateSetting -> {
                Toast.makeText(context, "업데이트 예정입니다." , Toast.LENGTH_SHORT).show()
                viewModel.processIntent(SettingsIntent.SetSettingsScreenState(SettingState.DefaultSetting))
            }

            SettingState.ThemeSetting -> {
                Toast.makeText(context, "업데이트 예정입니다." , Toast.LENGTH_SHORT).show()
                viewModel.processIntent(SettingsIntent.SetSettingsScreenState(SettingState.DefaultSetting))
            }
        }
    }

    if (showConfirmBackUpDialogIsOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable {
                    viewModel.processIntent(
                        SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen(
                            false
                        )
                    )
                }  // 배경을 클릭하면 다이얼로그를 닫음
        ) {
            AlertDialog(
                onDismissRequest = { viewModel.processIntent(SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen(false)) },
                title = { Text(text = "백업 작업") },
                text = { Text(text = "백업 작업을 진행하면 기존 데이터가 삭제됩니다. 계속하시겠습니까?") },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.padding(horizontal = 10.dp),
                        onClick = { viewModel.processIntent(SettingsIntent.Backup) }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    Button(
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.padding(horizontal = 10.dp),
                        onClick = { viewModel.processIntent(SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen(false)) }
                    ) {
                        Text("취소")
                    }
                }
            )
        }
    }
}