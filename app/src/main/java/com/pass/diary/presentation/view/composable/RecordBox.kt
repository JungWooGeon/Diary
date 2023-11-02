package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.presentation.ui.theme.BoxGray
import com.pass.diary.presentation.ui.theme.TextColor

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
