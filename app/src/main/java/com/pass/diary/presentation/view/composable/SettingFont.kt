package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.presentation.ui.theme.BoxGray
import com.pass.diary.presentation.ui.theme.TextColor
import com.pass.diary.presentation.view.screen.Constants.FONT_TO_KOREAN

@Composable
fun SettingFont(
    textSize: Float,
    textFont: String,
    onChangeFinishTextSize: (textSize: Float) -> Unit,
    onChangeFont: (font: String) -> Unit
) {
    val fontOptions = listOf("default", "garam", "skybori", "restart")
    var selectedFont by remember { mutableStateOf(textFont) }
    var sliderValue by remember { mutableFloatStateOf(textSize) }

    Column {
        Text(
            text = "폰트",
            modifier = Modifier.padding(top = 20.dp, start = 20.dp),
            fontSize = 16.sp
        )

        fontOptions.forEach { font ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .selectable(
                        selected = (font == selectedFont),
                        onClick = {
                            selectedFont = font
                            onChangeFont(font)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (font == selectedFont),
                    onClick = {
                        selectedFont = font
                        onChangeFont(font)
                    }
                )

                FONT_TO_KOREAN[font]?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(20.dp))

        Text(
            text = "일기 내용 텍스트 크기",
            modifier = Modifier.padding(top = 8.dp, start = 20.dp),
            fontSize = 16.sp
        )

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeFinishTextSize(sliderValue) },
            valueRange = 8f..32f,
            steps = 6,
            modifier = Modifier.padding(horizontal = 16.dp).padding(vertical = 10.dp)
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(BoxGray)
                    .padding(25.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "이모티콘과 함께 일기를 작성해보아요. 감정을 통계로 한 눈에 확인할 수 있어요. AI 한 줄 요약 기능으로 제목을 설정해보세요!",
                    style = LocalTextStyle.current.copy(color = TextColor, lineHeight = sliderValue.sp * 1.5),
                    fontSize = sliderValue.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}