package com.pass.presentation.view.screen

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.pass.domain.entity.Diary
import com.pass.presentation.intent.AddDiaryIntent
import com.pass.presentation.state.AddDiaryState
import com.pass.presentation.state.WorkState
import com.pass.presentation.view.composable.AddDiaryAppBar
import com.pass.presentation.view.composable.AddEmoticonDialog
import com.pass.presentation.view.composable.BottomEditor
import com.pass.presentation.view.composable.ContentBox
import com.pass.presentation.view.composable.CustomSpinnerDatePicker
import com.pass.presentation.view.composable.EmoticonBox
import com.pass.presentation.view.composable.RecordDialog
import com.pass.presentation.viewmodel.AddDiaryViewModel
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonState
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSButtonType
import com.simform.ssjetpackcomposeprogressbuttonlibrary.SSJetPackComposeProgressButton
import kotlinx.coroutines.delay
import java.lang.Math.PI

@Composable
fun AddDiaryScreen(diary: Diary?, viewModel: AddDiaryViewModel = hiltViewModel()) {
    val context = LocalContext.current

    // 현재 화면 상태 (standby, loading, error)
    val addDiaryState by viewModel.addDiaryState.collectAsState()

    // 텍스트 크기 상태
    val textSizeState by viewModel.textSizeState.collectAsState()

    // 선택한 날짜 상태
    val selectedDateWithLocalDate by viewModel.selectedDateWithLocalDate.collectAsState()

    // 제목 (요약 텍스트) 상태
    val titleTextState by viewModel.titleTextState.collectAsState()

    // 일기 내용 상태
    val contentTextState by viewModel.contentTextState.collectAsState()

    // '요약하기' 버튼 상태
    val submitButtonState by viewModel.submitButtonState.collectAsState()

    // 현재 선택된 이모티콘 리스트
    val emoticonIdListState by viewModel.emoticonIdListState.collectAsState()

    // DatePicker show / hide 상태
    val isDatePickerOpenState by viewModel.isDatePickerOpenState.collectAsState()

    // 이모티콘 추가 다이얼로그 상태
    val isDialogAddState by viewModel.isDialogAddState.collectAsState()

    // 이모티콘 삭제 다이얼로그 상태
    val isDialogEditState by viewModel.isDialogEditState.collectAsState()

    // 녹음 다이얼로그 상태
    val isRecordDialogState by viewModel.isRecordDialogState.collectAsState()

    // 삭제 다이얼로그 상태
    val isDeleteDialogState by viewModel.isDeleteDialogState.collectAsState()

    // 삭제 완료 상태 -> 삭제 성공 여부에 따른 토스트 메시지 출력
    val onCompleteDeleteEmotionState by viewModel.onCompleteDeleteEmotionState.collectAsState()
    LaunchedEffect(onCompleteDeleteEmotionState) {
        if (onCompleteDeleteEmotionState is WorkState.Standby) return@LaunchedEffect

        if (onCompleteDeleteEmotionState is WorkState.Success) {
            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
        } else if (onCompleteDeleteEmotionState is WorkState.Fail) {
            Toast.makeText(context, "1개까지 삭제할 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
        viewModel.processIntent(AddDiaryIntent.OnCompleteShowToastDeleteEmoticon)
    }

    // 이모티콘 추가 예외 처리 상태 -> true 시 에러 토스트 메시지 출력
    val addEmoticonErrorState by viewModel.addEmoticonErrorState.collectAsState()
    LaunchedEffect(addEmoticonErrorState) {
        if (addEmoticonErrorState) {
            Toast.makeText(context, "3개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            viewModel.processIntent(AddDiaryIntent.OnCompleteShowToastAddEmoticon)
        }
    }

    // 처음 시작 시 diary 상태에 따라 수정하기 / 추가하기 화면 구분
    LaunchedEffect(Unit) { viewModel.processIntent(AddDiaryIntent.Initialize(diary)) }

    // '요약하기' 성공 여부에 따라 토스트 메시지 출력
    LaunchedEffect(submitButtonState) {
        if (submitButtonState == SSButtonState.IDLE) return@LaunchedEffect

        // 3초 후 토스트 메시지 출력
        delay(3000)
        if (submitButtonState == SSButtonState.FAILIURE) {
            // 내용 요약 실패
            Toast.makeText(context, "내용을 요약할 수 없습니다. 더 자세히 적어주세요.", Toast.LENGTH_SHORT).show()
        } else if (submitButtonState == SSButtonState.SUCCESS) {
            // 내용 요약 성공 (요약 제목 반영)
            Toast.makeText(context, "요약된 내용이 제목에 반영되었어요!", Toast.LENGTH_SHORT).show()
        }

        viewModel.processIntent(AddDiaryIntent.SetIDleSSButtonState)
    }

    when (addDiaryState) {
        is AddDiaryState.Standby -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .background(Color.White),
                verticalArrangement = Arrangement.Top,
            ) {
                AddDiaryAppBar(
                    isEdit = diary != null,
                    context = context,
                    date = selectedDateWithLocalDate,
                    onAddDiary = { viewModel.processIntent(AddDiaryIntent.AddDiary) },
                    onOpenDatePicker = { viewModel.processIntent(AddDiaryIntent.UpdateDatePickerDialog(true)) },
                    onDeleteDiary = { viewModel.processIntent(AddDiaryIntent.UpdateDeleteDialog(true)) }
                )

                EmoticonBox(
                    emoticonIdList = emoticonIdListState,
                    onDatePickerOpen = { viewModel.processIntent(AddDiaryIntent.UpdateAddDialog(true)) },
                    onEmoticonChange = { viewModel.processIntent(AddDiaryIntent.UpdateEditDialog(it)) },
                    onEmotionDelete = { viewModel.processIntent(AddDiaryIntent.DeleteEmoticon(it)) }
                )

                val shouldShake by viewModel.shouldShake.collectAsState()

                val shake by animateFloatAsState(
                    targetValue = if (shouldShake) 1f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )

                // 제목
                ContentBox(
                    contentText = titleTextState,
                    hintText = "제목을 입력해주세요.",
                    modifier = Modifier
                        .offset(x = (kotlin.math.sin(shake * 2 * PI) * 10).dp)
                        .padding(horizontal = 20.dp),
                    textSize = textSizeState,
                    onTextChanged = { changedText -> viewModel.processIntent(AddDiaryIntent.WriteTitle(changedText)) }
                )

                // 본문
                ContentBox(
                    contentText = contentTextState,
                    hintText = "오늘은 무슨 일이 있었나요?",
                    modifier = Modifier
                        .weight(1F)
                        .padding(20.dp),
                    textSize = textSizeState,
                    onTextChanged = { changedText -> viewModel.processIntent(AddDiaryIntent.WriteContent(changedText)) }
                )

                // '수정하기' or '요약하기' 버튼
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SSJetPackComposeProgressButton(
                        type = SSButtonType.ZOOM_IN_OUT_CIRCLE,
                        width = LocalConfiguration.current.screenWidthDp.dp,
                        height = 50.dp,
                        onClick = { viewModel.processIntent(AddDiaryIntent.OnClickSSProgressButton) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black, contentColor = Color.White),
                        assetColor = Color.White,
                        buttonState = submitButtonState,
                        text = if (diary == null) { "내용 요약하기" } else { "수정 완료" }
                    )
                }

                BottomEditor(
                    onOpenRecordDialog = { viewModel.processIntent(AddDiaryIntent.UpdateRecordDialog(true)) },
                    onClickAddImage = { Toast.makeText(context, "업데이트 예정입니다.", Toast.LENGTH_SHORT).show() },
                    onClickSortImage =  { Toast.makeText(context, "업데이트 예정입니다.", Toast.LENGTH_SHORT).show() },
                )
            }

            // Emoticon Dialog show / hide
            if (isDialogAddState || isDialogEditState != Constants.NOT_EDIT_INDEX) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable {
                            // 배경을 클릭하면 다이얼로그를 닫음 (다이얼로그 상태 초기화)
                            viewModel.processIntent(AddDiaryIntent.UpdateAddDialog(false))
                            viewModel.processIntent(AddDiaryIntent.UpdateEditDialog(Constants.NOT_EDIT_INDEX))
                        }
                )

                AddEmoticonDialog(
                    onDismissRequest = {
                        // 배경을 클릭하면 다이얼로그를 닫음 (다이얼로그 상태 초기화)
                        viewModel.processIntent(AddDiaryIntent.UpdateAddDialog(false))
                        viewModel.processIntent(AddDiaryIntent.UpdateEditDialog(Constants.NOT_EDIT_INDEX))
                    },
                    onSelectEmoticon = { emoticonId -> viewModel.processIntent(AddDiaryIntent.OnSelectEmoticon(emoticonId)) }
                )
            }

            // DatePicker show / hide
            if (isDatePickerOpenState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable {
                            viewModel.processIntent(
                                AddDiaryIntent.UpdateDatePickerDialog(
                                    false
                                )
                            )
                        }  // 배경을 클릭하면 다이얼로그를 닫음
                )

                Dialog(onDismissRequest = { viewModel.processIntent(AddDiaryIntent.UpdateDatePickerDialog(false)) }) {
                    CustomSpinnerDatePicker(context) { tmpDate, isComplete ->
                        viewModel.processIntent(AddDiaryIntent.UpdateDatePickerDialog(false))

                        // 선택한 날짜 변경
                        if (isComplete) { viewModel.processIntent(AddDiaryIntent.SelectDate(tmpDate)) }
                    }
                }
            }

            // Record Dialog open / hide
            if (isRecordDialogState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { viewModel.processIntent(AddDiaryIntent.UpdateRecordDialog(false)) }  // 배경을 클릭하면 다이얼로그를 닫음
                ) {
                    RecordDialog(
                        onDismissRequest = { viewModel.processIntent(AddDiaryIntent.UpdateRecordDialog(false)) },
                        onCompleteRecording = {
                            viewModel.processIntent(AddDiaryIntent.UpdateRecordDialog(false))
                            viewModel.processIntent(AddDiaryIntent.WriteContent(contentTextState + "\n" + it))
                        }
                    )
                }
            }

            // Delete Dialog show / hide
            if (isDeleteDialogState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { viewModel.processIntent(AddDiaryIntent.UpdateDeleteDialog(false)) }  // 배경을 클릭하면 다이얼로그를 닫음
                ) {
                    AlertDialog(
                        onDismissRequest = { viewModel.processIntent(AddDiaryIntent.UpdateDeleteDialog(false)) },
                        title = { Text(text = "삭제") },
                        text = { Text(text = "일기를 삭제하시겠습니까?") },
                        confirmButton = {
                            Button(
                                shape = RoundedCornerShape(5.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Black),
                                modifier = Modifier.padding(horizontal = 10.dp),
                                onClick = { viewModel.processIntent(AddDiaryIntent.DeleteDiary) }
                            ) {
                                Text("확인")
                            }
                        },
                        dismissButton = {
                            Button(
                                shape = RoundedCornerShape(5.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Black),
                                modifier = Modifier.padding(horizontal = 10.dp),
                                onClick = { viewModel.processIntent(AddDiaryIntent.UpdateDeleteDialog(false)) }
                            ) {
                                Text("취소")
                            }
                        }
                    )
                }
            }
        }

        is AddDiaryState.Complete -> {
            if (diary == null) {
                Toast.makeText(context, "일기 작성이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "일기 수정/삭제가 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }

            (context as Activity).finish()
        }

        is AddDiaryState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
            }
        }

        is AddDiaryState.Error -> {
            val errorMessage = (addDiaryState as AddDiaryState.Error).error.message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage != null) {
                    Text(text = errorMessage)
                }
            }
        }
    }
}