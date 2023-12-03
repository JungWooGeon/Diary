package com.pass.diary.presentation.view.composable

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.pass.diary.R
import com.pass.diary.presentation.ui.theme.BoxGray

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordDialog(
    onDismissRequest: () -> Unit,
    onCompleteRecording: (text: String) -> Unit
) {
    val context = LocalContext.current
    val recordPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    var isPlaying by remember { mutableStateOf(false) }
    val progress by animateLottieCompositionAsState(
        composition, iterations = LottieConstants.IterateForever,
        isPlaying = isPlaying
    )

    var isResult by remember { mutableStateOf("") }

    val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    intent.putExtra(
        RecognizerIntent.EXTRA_CALLING_PACKAGE,
        context.packageName
    )
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")

    val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isPlaying = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> {
                    "오디오 에러"
                }

                SpeechRecognizer.ERROR_CLIENT -> {
                    "클라이언트 에러"
                }

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    "퍼미션 없음"
                }

                SpeechRecognizer.ERROR_NETWORK -> {
                    "네트워크 에러"
                }

                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    "네트워크 타임아웃"
                }

                SpeechRecognizer.ERROR_NO_MATCH -> {
                    "찾을 수 없음"
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    "RECOGNIZER가 바쁨"
                }

                SpeechRecognizer.ERROR_SERVER -> {
                    "서버가 이상함"
                }

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    "말하는 시간초과"
                }

                else -> {
                    "알 수 없는 오류"
                }
            }
            Toast.makeText(context, "녹음을 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            Log.d("녹음 오류", message)
        }

        override fun onResults(results: Bundle) {
            val matches =
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) //인식 결과를 담은 ArrayList

            var newText = ""
            if (matches != null) {
                for (i in matches.indices) {
                    newText += matches[i]
                }
            }

            isResult = newText

            // 사용자가 중지할 때까지 계속 녹음해야 하므로 녹음 재개
            // speechRecognizer.startListening(intent)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }


    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp)) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .wrapContentSize()
                    .padding(20.dp)
            ) {
                if (isResult != "") {
                    // 녹음 멈추기
                    speechRecognizer.stopListening()
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(BoxGray)
                                    .padding(25.dp)
                                    .fillMaxWidth()
                            ) { Text(text = isResult) }
                        }

                        Button(
                            onClick = { onCompleteRecording(isResult) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(text = "본문에 반영하기") }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!recordPermissionState.status.isGranted) {
                            if (recordPermissionState.status.shouldShowRationale) {
                                // 사용자가 권한 요청을 거부했지만 근거를 제시할 수 있는 경우, 앱에 이 권한이 필요한 이유를 친절하게 설명합니다.
                                Toast.makeText(
                                    context,
                                    "녹음 기능을 사용하려면 마이크 권한이 필요합니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val settingIntent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(
                                        Uri.parse("package:${context.packageName}")
                                    )
                                context.startActivity(settingIntent)
                                onDismissRequest()
                            } else {
                                // 사용자가 이 기능을 처음 사용하거나, 사용자에게 이 권한을 다시 묻고 싶지 않은 경우 권한이 필요하다고 설명합니다.
                                recordPermissionState.launchPermissionRequest()
                            }
                        } else {
                            LottieAnimation(
                                composition = composition,
                                progress = { progress },
                                modifier = Modifier
                                    .size(200.dp)
                                    .clickable {
                                        isPlaying = !isPlaying
                                        if (isPlaying) {
                                            //RecognizerIntent 객체 생성
                                            speechRecognizer.setRecognitionListener(listener)
                                            speechRecognizer.startListening(intent)
                                        } else {
                                            speechRecognizer.stopListening()
                                        }
                                    }
                            )

                            Text(text = "클릭하여 음성을 녹음해주세요")
                        }
                    }
                }
            }
        }
    }
}