package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.presentation.ui.theme.BoxGray
import com.pass.diary.presentation.ui.theme.TextColor

@Composable
fun ContentBox(
    modifier: Modifier,
    contentText: String,
    onTextChanged: (changedText: String) -> Unit,
    textSize: Float
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor, fontSize = textSize.sp, lineHeight = textSize.sp * 1.5),
                cursorBrush = SolidColor(Color.Black),
                decorationBox = { innerTextField ->
                    if (contentText.isEmpty()) {
                        // 입력된 텍스트가 없는 경우에만 플레이스홀더 보여주기
                        Text(
                            text = "오늘은 무슨 일이 있었나요?",
                            style = LocalTextStyle.current.copy(color = TextColor, lineHeight = textSize.sp * 1.5),
                            fontSize = textSize.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}