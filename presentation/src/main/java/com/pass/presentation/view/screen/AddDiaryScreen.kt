package com.pass.presentation.view.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.pass.domain.model.Diary
import com.pass.presentation.intent.AddDiaryIntent
import com.pass.presentation.state.AddDiaryState
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
import org.koin.androidx.compose.getViewModel
import java.time.LocalDate

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AddDiaryScreen(diary: Diary?, viewModel: AddDiaryViewModel = getViewModel()) {
    val context = LocalContext.current

    val addDiaryState by viewModel.state.collectAsState()
    val textSizeState by viewModel.textSizeState.collectAsState()
    val summaryState by viewModel.summaryState.collectAsState()

    // DatePicker 에서 사용될 selected date
    val dateArray = if (diary != null) {
        arrayListOf(diary.year, diary.month, diary.day)
    } else {
        arrayListOf(
            LocalDate.now().year.toString(),
            LocalDate.now().monthValue.toString(),
            LocalDate.now().dayOfMonth.toString()
        )
    }

    var selectedDateWithLocalDate by remember {
        mutableStateOf(
            LocalDate.of(
                dateArray[0].toInt(),
                dateArray[1].toInt(),
                dateArray[2].toInt()
            )
        )
    }

    var submitButtonState by remember { mutableStateOf(SSButtonState.IDLE) }

    // DatePicker show / hide 상태
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // 현재 선택된 이모티콘 리스트
    var emoticonIdList by remember {
        mutableStateOf(
            if (diary == null) {
                arrayListOf(Constants.EMOTICON_RAW_ID_LIST[0], -1, -1)
            } else {
                arrayListOf<Int>().apply {
                    (if (diary.emoticonId1 == null) -1 else diary.emoticonId1)?.let { add(it) }
                    (if (diary.emoticonId2 == null) -1 else diary.emoticonId2)?.let { add(it) }
                    (if (diary.emoticonId3 == null) -1 else diary.emoticonId3)?.let { add(it) }
                }
            }
        )
    }

    // 일기 제목
    var titleText by remember { mutableStateOf(diary?.title ?: "") }
    LaunchedEffect(summaryState) {
        // 요약 시 제목 변경 (실패 시 토스트 메시지 출력)
        submitButtonState = if (summaryState == "ERROR") { SSButtonState.FAILIURE } else if (summaryState != "") { SSButtonState.SUCCESS } else { SSButtonState.IDLE }

        // 내용 요약 실패
        if (submitButtonState == SSButtonState.FAILIURE) {
            Toast.makeText(context, "내용을 요약할 수 없습니다. 더 자세히 적어주세요.", Toast.LENGTH_SHORT).show()
            submitButtonState = SSButtonState.IDLE
            return@LaunchedEffect
        }

        // 요약 제목 반영
        if (summaryState != "") titleText = summaryState
    }

    // 일기 내용
    var contentText by remember { mutableStateOf(diary?.content ?: "") }

    // 이모티콘 다이얼로그 추가 / 수정 상태
    var isDialogAdd by remember { mutableStateOf(false) }
    var isDialogEdit by remember { mutableIntStateOf(Constants.NOT_EDIT_INDEX) }
    // 녹음 다이얼로그
    var isRecordDialogState by remember { mutableStateOf(false) }

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
                    onAddDiary = {
                        // 추가
                        var emoticonId1: Int? = null
                        var emoticonId2: Int? = null
                        var emoticonId3: Int? = null

                        if (emoticonIdList[0] != -1)
                            emoticonId1 = emoticonIdList[0]

                        if (emoticonIdList[1] != -1)
                            emoticonId2 = emoticonIdList[1]

                        if (emoticonIdList[2] != -1)
                            emoticonId3 = emoticonIdList[2]

                        val addDiary = Diary(
                            id = null,
                            year = selectedDateWithLocalDate.year.toString(),
                            month = selectedDateWithLocalDate.monthValue.toString(),
                            day = selectedDateWithLocalDate.dayOfMonth.toString(),
                            dayOfWeek = Constants.DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.dayOfWeek.toString()]!!,
                            emoticonId1 = emoticonId1,
                            emoticonId2 = emoticonId2,
                            emoticonId3 = emoticonId3,
                            imageUri = null,
                            content = contentText,
                            title = titleText
                        )

                        viewModel.processIntent(AddDiaryIntent.AddDiary(addDiary))
                    },
                    onOpenDatePicker = {
                        isDatePickerOpen = true
                    },
                    onDeleteDiary = {
                        // 삭제
                        diary?.let { viewModel.processIntent(AddDiaryIntent.DeleteDiary(diary)) }
                    }
                )

                EmoticonBox(
                    emoticonIdList = emoticonIdList,
                    onDatePickerOpen = { isDialogAdd = true },
                    onEmoticonChange = {
                        isDialogEdit = it
                    },
                    onEmotionDelete = {
                        if (emoticonIdList[1] == -1) {
                            Toast.makeText(context, "1개까지 삭제할 수 있습니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            val tmpList = arrayListOf<Int>()
                            when (it) {
                                0 -> {
                                    tmpList.add(emoticonIdList[1])
                                    tmpList.add(emoticonIdList[2])
                                    tmpList.add(-1)
                                }

                                1 -> {
                                    tmpList.add(emoticonIdList[0])
                                    tmpList.add(emoticonIdList[2])
                                    tmpList.add(-1)
                                }

                                2 -> {
                                    tmpList.add(emoticonIdList[0])
                                    tmpList.add(emoticonIdList[1])
                                    tmpList.add(-1)
                                }
                            }

                            emoticonIdList = tmpList

                            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 제목
                ContentBox(
                    contentText = titleText,
                    hintText = "제목을 입력해주세요.",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textSize = textSizeState,
                    onTextChanged = { changedText ->
                        titleText = changedText
                    }
                )

                // 본문
                ContentBox(
                    contentText = contentText,
                    hintText = "오늘은 무슨 일이 있었나요?",
                    modifier = Modifier
                        .weight(1F)
                        .padding(20.dp),
                    textSize = textSizeState,
                    onTextChanged = { changedText ->
                        contentText = changedText
                    }
                )

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
                        onClick = {
                            if (submitButtonState != SSButtonState.IDLE && submitButtonState != SSButtonState.SUCCESS) {
                                // 요약 중에는 버튼 클릭 방지
                                return@SSJetPackComposeProgressButton
                            }

                            if (diary == null) {
                                // 추가하기 일 때
                                if (contentText.length < 20) {
                                    // 내용이 너무 적을 경우 예외 처리
                                    Toast.makeText(context, "최소 20자이상 작성해주세요.", Toast.LENGTH_SHORT)
                                        .show()
                                    return@SSJetPackComposeProgressButton
                                }

                                // 요약하기
                                submitButtonState = SSButtonState.LOADING

                                titleText = ""
                                viewModel.processIntent(AddDiaryIntent.SummaryContent(titleText, contentText))
                            } else {
                                // 수정하기일 때
                                var emoticonId1: Int? = null
                                var emoticonId2: Int? = null
                                var emoticonId3: Int? = null

                                if (emoticonIdList[0] != -1)
                                    emoticonId1 = emoticonIdList[0]

                                if (emoticonIdList[1] != -1)
                                    emoticonId2 = emoticonIdList[1]

                                if (emoticonIdList[2] != -1)
                                    emoticonId3 = emoticonIdList[2]

                                diary.year = selectedDateWithLocalDate.year.toString()
                                diary.month = selectedDateWithLocalDate.monthValue.toString()
                                diary.day = selectedDateWithLocalDate.dayOfMonth.toString()
                                diary.dayOfWeek =
                                    Constants.DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.dayOfWeek.toString()]!!
                                diary.emoticonId1 = emoticonId1
                                diary.emoticonId2 = emoticonId2
                                diary.emoticonId3 = emoticonId3
                                diary.imageUri = null
                                diary.content = contentText
                                diary.title = titleText

                                viewModel.processIntent(AddDiaryIntent.UpdateDiary(diary))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Black, contentColor = Color.White),
                        assetColor = Color.White,
                        buttonState = submitButtonState,
                        text = if (diary == null) { "내용 요약하기" } else { "수정 완료" }
                    )
                }

                BottomEditor {
                    isRecordDialogState = true
                }
            }

            if (isDialogAdd || isDialogEdit != Constants.NOT_EDIT_INDEX) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable {
                            isDialogAdd = false
                            isDialogEdit = Constants.NOT_EDIT_INDEX
                        }  // 배경을 클릭하면 다이얼로그를 닫음
                )

                AddEmoticonDialog(
                    onDismissRequest = {
                        isDialogAdd = false
                        isDialogEdit = Constants.NOT_EDIT_INDEX
                    },
                    onSelectEmoticon = { emoticonId ->
                        if (isDialogEdit == Constants.NOT_EDIT_INDEX) {
                            // emoticon 추가
                            var index = -1
                            for (i in 0..<emoticonIdList.size) {
                                if (emoticonIdList[i] == -1) {
                                    index = i
                                    break
                                }
                            }

                            if (index != -1) {
                                emoticonIdList[index] = emoticonId
                            } else {
                                Toast.makeText(context, "3개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                            isDialogAdd = false
                        } else {
                            // emoticon 수정
                            emoticonIdList[isDialogEdit] = emoticonId
                            isDialogEdit = Constants.NOT_EDIT_INDEX
                        }
                    }
                )
            }

            // DatePicker show / hide
            if (isDatePickerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { isDatePickerOpen = false },  // 배경을 클릭하면 다이얼로그를 닫음
                )

                Dialog(onDismissRequest = { isDatePickerOpen = false }) {
                    CustomSpinnerDatePicker(context) { tmpDate, isComplete ->
                        isDatePickerOpen = false

                        if (isComplete) {
                            selectedDateWithLocalDate = tmpDate
                        }
                    }
                }
            }

            // Record Dialog open / hide
            if (isRecordDialogState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable {
                            isRecordDialogState = false
                        }  // 배경을 클릭하면 다이얼로그를 닫음
                ) {
                    RecordDialog(
                        onDismissRequest = { isRecordDialogState = false },
                        onCompleteRecording = {
                            isRecordDialogState = false
                            contentText += "\n" + it
                        }
                    )
                }
            }
        }

        is AddDiaryState.Complete -> {
            val returnIntent = Intent()

            if (diary == null) {
                Toast.makeText(context, "일기 작성이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                (context as Activity).setResult(Activity.RESULT_OK, returnIntent)
            } else {
                Toast.makeText(context, "일기 수정/삭제가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                (context as Activity).setResult(Activity.RESULT_OK, returnIntent)
            }

            context.finish()
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