package com.pass.diary.presentation.view.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.pass.diary.R
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.intent.AddDiaryIntent
import com.pass.diary.presentation.state.AddDiaryState
import com.pass.diary.presentation.ui.theme.BoxGray
import com.pass.diary.presentation.ui.theme.DiaryTheme
import com.pass.diary.presentation.ui.theme.LineGray
import com.pass.diary.presentation.ui.theme.TextColor
import com.pass.diary.presentation.view.screen.Constants.DAY_OF_WEEK_TO_KOREAN
import com.pass.diary.presentation.view.screen.Constants.EMOTICON_RAW_ID_LIST
import com.pass.diary.presentation.view.screen.Constants.INTENT_NAME_DATE
import com.pass.diary.presentation.view.screen.Constants.NOT_EDIT_INDEX
import com.pass.diary.presentation.viewmodel.AddDiaryViewModel
import org.koin.androidx.compose.getViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AddDiaryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var date = intent.getStringExtra(INTENT_NAME_DATE)

        if (date == null) {
            date =
                LocalDate.now().year.toString() + "." + LocalDate.now().monthValue + "." + LocalDate.now().dayOfMonth.toString()
        }

        setContent {
            DiaryTheme {
                AddDiaryScreen(date)
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AddDiaryScreen(date: String, viewModel: AddDiaryViewModel = getViewModel()) {
    val context = LocalContext.current

    val addDiaryState by viewModel.state.collectAsState()

    // DatePicker 에서 사용될 selected date
    val dateArray = date.split(".")
    var selectedDateWithLocalDate by remember {
        mutableStateOf(
            LocalDate.of(
                dateArray[0].toInt(),
                dateArray[1].toInt(),
                dateArray[2].toInt()
            )
        )
    }

    // DatePicker show / hide 상태
    var isDatePickerOpen by remember { mutableStateOf(false) }

    // 현재 선택된 이모티콘 리스트
    val emoticonIdList by remember { mutableStateOf(arrayListOf(EMOTICON_RAW_ID_LIST[0])) }

    // 일기 내용
    var contentText by remember { mutableStateOf("") }

    // 이모티콘 다이얼로그 추가 / 수정 상태
    var isDialogAdd by remember { mutableStateOf(false) }
    var isDialogEdit by remember { mutableIntStateOf(NOT_EDIT_INDEX) }

    when (addDiaryState) {
        is AddDiaryState.Standby -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .background(Color.White),
                verticalArrangement = Arrangement.Top,
            ) {
                AppBar(
                    context = context,
                    date = selectedDateWithLocalDate,
                    onAddDiary = {
                        var emoticonId1: Int? = null
                        var emoticonId2: Int? = null
                        var emoticonId3: Int? = null

                        if (emoticonIdList.size >= 1)
                            emoticonId1 = emoticonIdList[0]

                        if (emoticonIdList.size >= 2)
                            emoticonId2 = emoticonIdList[1]

                        if (emoticonIdList.size >= 3)
                            emoticonId3 = emoticonIdList[2]

                        val diary = Diary(
                            id = null,
                            year = selectedDateWithLocalDate.year.toString(),
                            month = selectedDateWithLocalDate.monthValue.toString(),
                            day = selectedDateWithLocalDate.dayOfMonth.toString(),
                            dayOfWeek = DAY_OF_WEEK_TO_KOREAN[selectedDateWithLocalDate.dayOfWeek.toString()]!!,
                            emoticonId1 = emoticonId1,
                            emoticonId2 = emoticonId2,
                            emoticonId3 = emoticonId3,
                            null,
                            null,
                            content = contentText
                        )

                        viewModel.processIntent(AddDiaryIntent.AddDiary(diary))
                    },
                    onOpenDatePicker = {
                        isDatePickerOpen = true
                    }
                )

                EmoticonBox(
                    emoticonIdList = emoticonIdList,
                    onDatePickerOpen = { isDialogAdd = true },
                    onEmoticonChange = {
                        isDialogEdit = it
                    }
                )

                RecordBox()

                ContentBox(
                    contentText = contentText,
                    modifier = Modifier.weight(1F)
                ) { changedText ->
                    contentText = changedText
                }

                Button(
                    onClick = { /*TODO*/ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "내용 요약하기")
                }

                BottomEditor()
            }

            if (isDialogAdd || isDialogEdit != NOT_EDIT_INDEX) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))  // 배경을 반투명한 검정색으로 설정
                        .clickable { isDialogAdd = false },  // 배경을 클릭하면 다이얼로그를 닫음
                )

                AddEmoticonDialog(
                    onDismissRequest = {
                        isDialogAdd = false
                    },
                    onSelectEmoticon = { emoticonId ->
                        if (isDialogEdit == NOT_EDIT_INDEX) {
                            // emoticon 추가
                            if (emoticonIdList.size < 3) {
                                emoticonIdList.add(emoticonId)
                            } else {
                                Toast.makeText(context, "3개까지 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
                            }
                            isDialogAdd = false
                        } else {
                            // emoticon 수정
                            emoticonIdList[isDialogEdit] = emoticonId
                            isDialogEdit = NOT_EDIT_INDEX
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

                val datePicker =
                    MaterialDatePicker.Builder.datePicker()
                        .setTitleText("Select date")
                        .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                        .build()

                datePicker.addOnPositiveButtonClickListener {
                    selectedDateWithLocalDate = Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                datePicker.show((context as AppCompatActivity).supportFragmentManager, "DATE_PICKER")
            }
        }

        is AddDiaryState.Complete -> {
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

@Composable
fun AppBar(
    context: Context,
    date: LocalDate,
    onAddDiary: () -> Unit,
    onOpenDatePicker: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = { (context as Activity).finish() }) {
            Icon(Icons.Default.KeyboardArrowLeft, "Back Button")
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp)
        ) {
            Text(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                text = date.year.toString() + "년 " + date.monthValue + "월 " + date.dayOfMonth + "일"
            )

            IconButton(onClick = { onOpenDatePicker() }) {
                Icon(Icons.Default.ArrowDropDown, "DatePicker Button")
            }
        }

        Text(
            text = "완료",
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(end = 20.dp)
                .clickable {
                    onAddDiary()
                }
        )
    }
}

@Composable
fun EmoticonBox(
    emoticonIdList: ArrayList<Int>,
    onDatePickerOpen: () -> Unit,
    onEmoticonChange: (index: Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .wrapContentHeight()
    ) {
        Box(
            modifier = Modifier
                .background(BoxGray)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    contentColor = contentColorFor(backgroundColor = Color.White),
                    modifier = Modifier.size(30.dp)
                ) {
                    IconButton(onClick = { onDatePickerOpen() }) {
                        Icon(Icons.Rounded.Add, contentDescription = "이모티콘 추가")
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                LazyRow {
                    itemsIndexed(emoticonIdList) { index, emoticonId ->
                        EmoticonItem(emoticonId) {
                            onEmoticonChange(index)
                        }
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun EmoticonItem(
    emoticonId: Int,
    onEmoticonChange: () -> Unit
) {
    IconButton(onClick = { onEmoticonChange() }) {
        Image(
            painter = painterResource(id = emoticonId),
            contentDescription = "이모티콘",
            modifier = Modifier.size(50.dp)
        )
    }

}

@Composable
fun RecordBox() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .wrapContentHeight()
    ) {
        Box(
            modifier = Modifier
                .background(BoxGray)
                .padding(25.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    drawCircle(
                        color = Color.Red,
                        radius = size.minDimension / 2,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }

                Spacer(modifier = Modifier.size(20.dp))

                Text(
                    text = "오늘의 이야기를 들려주세요.",
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = TextColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun ContentBox(
    modifier: Modifier,
    contentText: String,
    onTextChanged: (changedText: String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .background(BoxGray)
                .padding(25.dp)
                .fillMaxWidth()
        ) {
            BasicTextField(
                value = contentText,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent),
                textStyle = TextStyle(color = TextColor),
                cursorBrush = SolidColor(Color.Black),
                decorationBox = { innerTextField ->
                    if (contentText.isEmpty()) {
                        // 입력된 텍스트가 없는 경우에만 플레이스홀더 보여주기
                        Text(
                            text = "오늘은 무슨 일이 있었나요?",
                            style = LocalTextStyle.current.copy(color = TextColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
fun AddEmoticonDialog(
    onDismissRequest: () -> Unit,
    onSelectEmoticon: (emoticonId: Int) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp)) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .wrapContentSize()
                    .padding(20.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.wrapContentSize(),
                ) {
                    items(EMOTICON_RAW_ID_LIST.size) { index ->  // 100개의 아이템을 생성
                        // 각 아이템을 그리는 코드를 작성합니다.
                        // 예를 들어, Text Composable을 사용하여 index를 출력할 수 있습니다.
                        IconButton(onClick = { onSelectEmoticon(EMOTICON_RAW_ID_LIST[index]) }) {
                            Image(
                                painter = painterResource(id = EMOTICON_RAW_ID_LIST[index]),
                                contentDescription = "감정"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomEditor() {
    Divider(
        color = LineGray,
        thickness = 1.dp,
        modifier = Modifier.padding(top = 10.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(start = 10.dp)
    ) {
        IconButton(onClick = { }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_image),
                contentDescription = "이미지 추가"
            )
        }

        IconButton(onClick = { }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sort),
                contentDescription = "정렬"
            )
        }
    }
}