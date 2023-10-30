package com.pass.diary.presentation.view.activity

import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.R
import com.pass.diary.presentation.ui.theme.BoxGray
import com.pass.diary.presentation.ui.theme.DiaryTheme
import com.pass.diary.presentation.ui.theme.LineGray
import com.pass.diary.presentation.ui.theme.TextColor
import com.pass.diary.presentation.view.screen.Constants.INTENT_NAME_DATE
import java.time.LocalDate

class AddDiaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var date = intent.getStringExtra(INTENT_NAME_DATE)
        if (date == null) {
            date =
                LocalDate.now().year.toString() + "." + LocalDate.now().monthValue + "." + LocalDate.now().dayOfMonth
        }

        setContent {
            DiaryTheme {
                AddDiaryScreen(date)
            }
        }
    }
}

@Composable
fun AddDiaryScreen(date: String) {
    val emoticonIdList by remember { mutableStateOf(arrayListOf(R.drawable.em_smile)) }

    var contentText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Top,
    ) {
        AppBar(date)

        EmoticonBox(emoticonIdList)

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
                Icon(painter = painterResource(id = R.drawable.ic_sort), contentDescription = "정렬")
            }
        }
    }
}

@Composable
fun AppBar(date: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(onClick = { }) {
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
                text = date
            )

            IconButton(onClick = { }) {
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

                }
        )
    }
}

@Composable
fun EmoticonBox(emoticonIdList: ArrayList<Int>) {
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
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.Add, contentDescription = "이모티콘 추가")
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                LazyRow {
                    items(emoticonIdList) { emoticonId ->
                        EmoticonItem(emoticonId)
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
fun EmoticonItem(emoticonId: Int) {
    Image(
        painter = painterResource(id = emoticonId),
        contentDescription = "이모티콘",
        modifier = Modifier.size(50.dp)
    )
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

@Preview
@Composable
fun PreviewAddDiaryScreen() {
    DiaryTheme {
        AddDiaryScreen(LocalDate.now().year.toString() + "." + LocalDate.now().monthValue + "." + LocalDate.now().dayOfMonth)
    }
}