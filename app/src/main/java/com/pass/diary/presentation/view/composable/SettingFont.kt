package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingFont(
    textSize: Float,
    onChangeFinishTextSize: (textSize: Float) -> Unit
) {
    val fontOptions = listOf("Font1", "Font2", "Font3")
    var selectedFont by remember { mutableStateOf(fontOptions[0]) }

    var sliderValue by remember { mutableFloatStateOf(textSize) }

    Column {
        Text(
            text = "폰트",
            modifier = Modifier.padding(top = 8.dp, start = 20.dp),
            fontSize = sliderValue.sp
        )

        fontOptions.forEach { font ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .selectable(
                        selected = (font == selectedFont),
                        onClick = { selectedFont = font }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (font == selectedFont),
                    onClick = { selectedFont = font }
                )

                Text(
                    text = font,
                    style = MaterialTheme.typography.body1.merge().copy(fontSize = sliderValue.sp),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        Text(
            text = "텍스트 크기",
            modifier = Modifier.padding(top = 8.dp, start = 20.dp),
            fontSize = sliderValue.sp
        )

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeFinishTextSize(sliderValue) },
            valueRange = 8f..32f,
            steps = 6,
            modifier = Modifier.padding(16.dp)
        )
    }
}