package com.pass.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import com.pass.presentation.ui.theme.BoxGray

@Composable
fun EmoticonBox(
    emoticonIdList: ArrayList<Int>,
    onDatePickerOpen: () -> Unit,
    onEmoticonChange: (index: Int) -> Unit,
    onEmotionDelete: (index: Int) -> Unit
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
//                        Icon(Icons.Rounded.Add, contentDescription = "이모티콘 추가")
                        Icon(imageVector = Icons.Default.Add, contentDescription = "")
                    }
                }

                Spacer(modifier = Modifier.size(10.dp))

                LazyRow {
                    itemsIndexed(emoticonIdList) { index, emoticonId ->
                        if (emoticonId != -1) {
                            EmoticonItem(
                                emoticonId = emoticonId,
                                onEmoticonChange = {
                                    onEmoticonChange(index)
                                },
                                onEmotionDelete = {
                                    onEmotionDelete(index)
                                }
                            )
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
    onEmoticonChange: () -> Unit,
    onEmotionDelete: () -> Unit
) {
    var touchPosition by remember { mutableStateOf(IntOffset.Zero) }
    var showPopup by remember { mutableStateOf(false) }

    Image(
        painter = painterResource(id = emoticonId),
        contentDescription = "이모티콘",
        modifier = Modifier
            .size(70.dp)
            .padding(horizontal = 10.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onEmoticonChange()
                    },
                    onLongPress = { offset ->
                        touchPosition = offset.round()
                        showPopup = true
                    }
                )
            }
    )

    if (showPopup) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(touchPosition.x, touchPosition.y),
            onDismissRequest = { showPopup = false }
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .padding(10.dp)
            ) {
                Text("삭제", modifier = Modifier.clickable {
                    showPopup = false
                    onEmotionDelete()
                })
            }
        }
    }
}