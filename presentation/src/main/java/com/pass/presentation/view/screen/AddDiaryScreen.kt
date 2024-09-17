package com.pass.presentation.view.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pass.presentation.sideeffect.AddDiarySideEffect
import com.pass.presentation.state.screen.AddDiaryLoadingState
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
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import java.lang.Math.PI
import java.time.LocalDate

@Composable
fun AddDiaryScreen(diary: Diary?, viewModel: AddDiaryViewModel = hiltViewModel()) {

    val context = LocalContext.current
    val addDiaryState = viewModel.collectAsState().value

    // 제목 (요약 텍스트) 상태 - 수정일 때, diary title 초기값
    var titleText by remember { mutableStateOf(diary?.title ?: "") }

    // 일기 내용 상태 - 수정일 때, diary content 초기값
    var contentText by remember { mutableStateOf(diary?.content ?: "") }

    // ContentBox 흔들기 animation
    val shakeContentBox by animateFloatAsState(
        targetValue = if (addDiaryState.shouldShake) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    viewModel.collectSideEffect { sideEffect ->
        when(sideEffect) {
            is AddDiarySideEffect.Toast -> {
                Toast.makeText(context, sideEffect.text, Toast.LENGTH_SHORT).show()
            }
            is AddDiarySideEffect.ChangeContent -> { contentText = sideEffect.text }
            is AddDiarySideEffect.ChangeTitle -> { titleText = sideEffect.text }
        }
    }

    // 처음 시작 시 diary 상태에 따라 수정하기 / 추가하기 화면 구분
    LaunchedEffect(Unit) { viewModel.processIntent(AddDiaryIntent.Initialize(diary)) }

    AddDiaryScreen(
        context = context,
        loadingState = addDiaryState.loading,
        isEditScreen = (diary != null),
        textSize = addDiaryState.textSizeState,
        selectedDateWithLocalDate = addDiaryState.selectedDateWithLocalDate,
        titleText = titleText,
        contentText = contentText,
        submitButtonState = addDiaryState.submitButtonState,
        emoticonIdList = addDiaryState.emoticonIdList,
        isDatePickerOpenState = addDiaryState.isDatePickerOpenState,
        isDialogAddState = addDiaryState.isDialogAddState,
        isDialogEditState = addDiaryState.isDialogEditState,
        isDeleteDialogState = addDiaryState.isDeleteDialogState,
        isRecordDialogState = addDiaryState.isRecordDialogState,
        shakeContentBox = shakeContentBox,
        onAddDiary = {
            viewModel.processIntent(
                AddDiaryIntent.AddDiary(contentText = contentText, titleText = titleText)
            )
        },
        onUpdateDatePickerDialog = { viewModel.processIntent(AddDiaryIntent.UpdateDatePickerDialog(it)) },
        onUpdateDeleteDialog = { viewModel.processIntent(AddDiaryIntent.UpdateDeleteDialog(it)) },
        onUpdateEmoticonAddDialog = { viewModel.processIntent(AddDiaryIntent.UpdateAddDialog(it)) },
        onUpdateEmoticonEditDialog = { viewModel.processIntent(AddDiaryIntent.UpdateEditDialog(it)) },
        onUpdateRecordDialog = { viewModel.processIntent(AddDiaryIntent.UpdateRecordDialog(it)) },
        onDeleteEmoticon = { viewModel.processIntent(AddDiaryIntent.DeleteEmoticon(it)) },
        onChangeTitle = { titleText = it },
        onChangeContent = { contentText = it },
        onClickSSProgressButton = { viewModel.processIntent(AddDiaryIntent.OnClickSSProgressButton(
            contentText = contentText,
            titleText = titleText
        )) },
        onSelectEmoticon = { emoticonId -> viewModel.processIntent(AddDiaryIntent.OnSelectEmoticon(emoticonId)) },
        onSelectDate = { date -> viewModel.processIntent(AddDiaryIntent.SelectDate(date)) },
        onDeleteDiary = { viewModel.processIntent(AddDiaryIntent.DeleteDiary) }
    )
}

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun AddDiaryScreen(
    context: Context,
    loadingState: AddDiaryLoadingState,
    isEditScreen: Boolean,
    textSize: Float,
    selectedDateWithLocalDate: LocalDate,
    titleText: String,
    contentText: String,
    submitButtonState: SSButtonState,
    emoticonIdList: List<Int>,
    isDatePickerOpenState: Boolean,
    isDialogAddState: Boolean,
    isDialogEditState: Int,
    isDeleteDialogState: Boolean,
    isRecordDialogState: Boolean,
    shakeContentBox: Float,
    onAddDiary: () -> Unit,
    onUpdateDatePickerDialog: (Boolean) -> Unit,
    onUpdateDeleteDialog: (Boolean) -> Unit,
    onUpdateEmoticonAddDialog: (Boolean) -> Unit,
    onUpdateEmoticonEditDialog: (Int) -> Unit,
    onUpdateRecordDialog: (Boolean) -> Unit,
    onDeleteEmoticon: (Int) -> Unit,
    onChangeTitle: (String) -> Unit,
    onChangeContent: (String) -> Unit,
    onClickSSProgressButton: () -> Unit,
    onSelectEmoticon: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onDeleteDiary: () -> Unit,
 ) {
    when (loadingState) {
        is AddDiaryLoadingState.Standby -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                verticalArrangement = Arrangement.Top,
            ) {
                AddDiaryAppBar(
                    isEdit = isEditScreen,
                    context = context,
                    date = selectedDateWithLocalDate,
                    onAddDiary = onAddDiary,
                    onOpenDatePicker = { onUpdateDatePickerDialog(true) },
                    onDeleteDiary = { onUpdateDeleteDialog(true) }
                )

                EmoticonBox(
                    emoticonIdList = emoticonIdList,
                    onDatePickerOpen = { onUpdateEmoticonAddDialog(true) },
                    onEmoticonChange = onUpdateEmoticonEditDialog,
                    onEmotionDelete = onDeleteEmoticon
                )

                // 제목
                ContentBox(
                    contentText = titleText,
                    hintText = "제목을 입력해주세요.",
                    modifier = Modifier
                        .offset(x = (kotlin.math.sin(shakeContentBox * 2 * PI) * 10).dp)
                        .padding(horizontal = 20.dp),
                    textSize = textSize,
                    onTextChanged = onChangeTitle
                )

                // 본문
                ContentBox(
                    contentText = contentText,
                    hintText = "오늘은 무슨 일이 있었나요?",
                    modifier = Modifier
                        .weight(1F)
                        .padding(20.dp),
                    textSize = textSize,
                    onTextChanged = onChangeContent
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
                        onClick = onClickSSProgressButton,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black, contentColor = Color.White),
                        assetColor = Color.White,
                        buttonState = submitButtonState,
                        text = if (!isEditScreen) { "내용 요약하기" } else { "수정 완료" }
                    )
                }

                BottomEditor(
                    onOpenRecordDialog = { onUpdateRecordDialog(true) },
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
                            onUpdateEmoticonAddDialog(false)
                            onUpdateEmoticonEditDialog(Constants.NOT_EDIT_INDEX)
                        }
                )

                AddEmoticonDialog(
                    onDismissRequest = {
                        // 배경을 클릭하면 다이얼로그를 닫음 (다이얼로그 상태 초기화)
                        onUpdateEmoticonAddDialog(false)
                        onUpdateEmoticonEditDialog(Constants.NOT_EDIT_INDEX)
                    },
                    onSelectEmoticon = { emoticonId -> onSelectEmoticon(emoticonId) }
                )
            }

            // DatePicker show / hide
            if (isDatePickerOpenState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { onUpdateDatePickerDialog(false) }  // 배경을 클릭하면 다이얼로그를 닫음
                )

                Dialog(onDismissRequest = { onUpdateDatePickerDialog(false) }) {
                    CustomSpinnerDatePicker(context) { tmpDate, isComplete ->
                        onUpdateDatePickerDialog(false)

                        // 선택한 날짜 변경
                        if (isComplete) { onSelectDate(tmpDate) }
                    }
                }
            }

            // Record Dialog open / hide
            if (isRecordDialogState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { onUpdateRecordDialog(false) }  // 배경을 클릭하면 다이얼로그를 닫음
                ) {
                    RecordDialog(
                        onDismissRequest = { onUpdateRecordDialog(false) },
                        onCompleteRecording = {
                            onUpdateRecordDialog(false)
                            onChangeContent(if (contentText == "") it else contentText + "\n" + it)
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
                        .clickable { onUpdateDeleteDialog(false) }  // 배경을 클릭하면 다이얼로그를 닫음
                ) {
                    AlertDialog(
                        onDismissRequest = { onUpdateDeleteDialog(false) },
                        title = { Text(text = "삭제") },
                        text = { Text(text = "일기를 삭제하시겠습니까?") },
                        confirmButton = {
                            Button(
                                shape = RoundedCornerShape(4.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Black),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                onClick = onDeleteDiary
                            ) {
                                Text("확인")
                            }
                        },
                        dismissButton = {
                            Button(
                                shape = RoundedCornerShape(4.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Black),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                onClick = { onUpdateDeleteDialog(false) }
                            ) {
                                Text("취소")
                            }
                        }
                    )
                }
            }
        }

        is AddDiaryLoadingState.Complete -> {
            if (!isEditScreen) {
                Toast.makeText(context, "일기 작성이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "일기 수정/삭제가 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }

            (context as Activity).finish()
        }

        is AddDiaryLoadingState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("LoadingIndicator"))
            }
        }

        is AddDiaryLoadingState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                loadingState.error.message?.let { Text(text = it) }
            }
        }
    }
}