package com.pass.presentation.view.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.pass.presentation.sideeffect.SettingSideEffect
import com.pass.presentation.state.route.SettingRouteState
import com.pass.presentation.ui.theme.LineGray
import com.pass.presentation.view.composable.SettingBackup
import com.pass.presentation.view.composable.SettingDefault
import com.pass.presentation.view.composable.SettingFont
import com.pass.presentation.view.composable.SettingLicense
import com.pass.presentation.view.composable.SettingLock
import com.pass.presentation.viewmodel.SettingsViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {

    val context = LocalContext.current
    val settingsState = viewModel.collectAsState().value

    // 권한 허용 완료 후 로그인 창 띄우기
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.processIntent(SettingsIntent.Login(activityResultData = it.data))
    }

    // 로그인 화면 요청
    val googleSignInOptions = GoogleSignInOptions
        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(BuildConfig.default_web_client_id)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    // 구글 드라이브 권한 허용 요청 launcher
    val driveLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 권한 요청이 승인되었을 때 대기 중인 작업을 실행
            viewModel.processIntent(settingsState.drivePendingActionIntent)
        } else {
            // 권한 요청이 거부되었을 때 토스트 메시지 출력
            Toast.makeText(context, "권한 요청이 거부되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    viewModel.collectSideEffect { sideEffect ->  
        when(sideEffect) {
            is SettingSideEffect.Toast -> {
                Toast.makeText(context, sideEffect.text, Toast.LENGTH_SHORT).show()
            }

            is SettingSideEffect.RequestGoogleDrivePermission -> {
                driveLauncher.launch(sideEffect.error)
            }
        }
    }

    BackHandler(
        enabled = settingsState.settingRouteState != SettingRouteState.DefaultSettingRoute
    ) {
        viewModel.processIntent(SettingsIntent.OnNavigateSettingsScreen(SettingRouteState.DefaultSettingRoute))
    }

    SettingsScreen(
        settingRouteState = settingsState.settingRouteState,
        isOpenConfirmBackUpDialog = settingsState.isOpenConfirmBackUpDialog,
        isBackUpLoading = settingsState.isBackUpLoading,
        textSize = settingsState.textSize,
        textFont = settingsState.textFont,
        isLoggedIn = settingsState.isLoggedIn,
        isLock = settingsState.password != "",
        onNavigateSettingsScreen = { viewModel.processIntent(SettingsIntent.OnNavigateSettingsScreen(it)) },
        onNavigatePrivacyPolicyScreen = {
            val url = "https://ai-diary-privacy-policy.co.kr/"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        },
        onNavigateReviewScreen = {
            val uri = Uri.parse("market://details?id=" + context.packageName)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        },
        onNavigateLicenseScreen = { url ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        },
        onSignIn = { launcher.launch(googleSignInClient.signInIntent) },
        onSignOut = {
            viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(true))
            viewModel.processIntent(SettingsIntent.Logout)
            googleSignInClient.signOut().addOnCompleteListener {
                viewModel.processIntent(SettingsIntent.UpdateBackUpLoadingState(false))
                Toast.makeText(context, "로그아웃이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }
        },
        onUpdateShowConfirmBackUpDialog = {
            viewModel.processIntent(SettingsIntent.UpdateShowConfirmBackUpDialogIsOpen(it))
        },
        onBackUp = { viewModel.processIntent(SettingsIntent.Backup) },
        onRestore = { viewModel.processIntent(SettingsIntent.Restore) },
        onShowToast = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
        onUpdateCurrentTextSize = { viewModel.processIntent(SettingsIntent.UpdateCurrentTextSize(it)) },
        onUpdateCurrentFont = { viewModel.processIntent(SettingsIntent.UpdateCurrentFont(it)) },
        onUpdatePassword = { viewModel.processIntent(SettingsIntent.UpdatePassword(it)) }
    )
}

@Composable
fun SettingsScreen(
    settingRouteState: SettingRouteState,
    isOpenConfirmBackUpDialog: Boolean,
    isBackUpLoading: Boolean,
    textSize: Float,
    textFont: String,
    isLoggedIn: Boolean,
    isLock: Boolean,
    onNavigateSettingsScreen: (SettingRouteState) -> Unit,
    onNavigatePrivacyPolicyScreen: () -> Unit,
    onNavigateReviewScreen: () -> Unit,
    onNavigateLicenseScreen: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUpdateShowConfirmBackUpDialog: (Boolean) -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onShowToast: (String) -> Unit,
    onUpdateCurrentTextSize: (Float) -> Unit,
    onUpdateCurrentFont: (String) -> Unit,
    onUpdatePassword: (String) -> Unit
) {
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
            if (settingRouteState != SettingRouteState.DefaultSettingRoute) {
                IconButton(
                    onClick = { onNavigateSettingsScreen(SettingRouteState.DefaultSettingRoute) },
                    modifier = Modifier
                        .weight(0.2f)
                        .size(20.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 화면")
                }
                Spacer(modifier = Modifier.weight(0.8f))
            } else {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .size(20.dp))
            }
            Text(
                text = "설정",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            color = LineGray,
            thickness = 1.dp,
            modifier = Modifier.padding(top = 10.dp)
        )

        when (settingRouteState) {
            SettingRouteState.DefaultSettingRoute -> {
                SettingDefault(
                    onClick = { onNavigateSettingsScreen(it) },
                    onClickPrivacyPolicy = onNavigatePrivacyPolicyScreen,
                    onClickReview = onNavigateReviewScreen
                )
            }

            SettingRouteState.FontSettingRoute -> {
                SettingFont(
                    textSize = textSize,
                    textFont = textFont,
                    onChangeFinishTextSize = onUpdateCurrentTextSize,
                    onChangeFont = onUpdateCurrentFont
                )
            }

            SettingRouteState.BackupSettingRoute -> {
                if (isBackUpLoading) {
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
                        isLoggedIn = isLoggedIn,
                        signIn = onSignIn,
                        signOut = onSignOut,
                        backUp = { onUpdateShowConfirmBackUpDialog(true) },
                        restore = onRestore
                    )
                }
            }

            SettingRouteState.LicenseSettingRoute -> {
                SettingLicense(onClickUrl = { url -> onNavigateLicenseScreen(url) })
            }

            SettingRouteState.NotificationSettingRoute -> {
                onShowToast("업데이트 예정입니다.")
                onNavigateSettingsScreen(SettingRouteState.DefaultSettingRoute)
            }

            SettingRouteState.ScreenLockSettingRoute -> {
                SettingLock(
                    isLock = isLock,
                    onClickChangePassword = {
                        if (isLock) onNavigateSettingsScreen(SettingRouteState.LockPasswordSettingRoute)
                        else onShowToast("비밀번호를 설정해주세요.")
                    },
                    onTogglePassword = {
                        if (isLock) { onUpdatePassword("") }
                        else { onNavigateSettingsScreen(SettingRouteState.LockPasswordSettingRoute) }
                    }
                )
            }

            SettingRouteState.LockPasswordSettingRoute -> {
                LockScreen(
                    onComplete = {
                        onUpdatePassword(it)
                        onNavigateSettingsScreen(SettingRouteState.ScreenLockSettingRoute)
                    }
                )
            }

            SettingRouteState.StartDateSettingRoute -> {
                onShowToast("업데이트 예정입니다.")
                onNavigateSettingsScreen(SettingRouteState.DefaultSettingRoute)
            }
        }
    }

    if (isOpenConfirmBackUpDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                .clickable { onUpdateShowConfirmBackUpDialog(false) }  // 배경을 클릭하면 다이얼로그를 닫음
        ) {
            AlertDialog(
                onDismissRequest = { onUpdateShowConfirmBackUpDialog(false) },
                title = { Text(text = "백업 작업") },
                text = { Text(text = "백업 작업을 진행하면 기존 데이터가 삭제됩니다. 계속하시겠습니까?") },
                confirmButton = {
                    Button(
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.padding(horizontal = 10.dp),
                        onClick = onBackUp
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    Button(
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.padding(horizontal = 10.dp),
                        onClick = { onUpdateShowConfirmBackUpDialog(false) }
                    ) {
                        Text("취소")
                    }
                }
            )
        }
    }
}