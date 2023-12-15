package com.pass.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pass.presentation.view.screen.Constants

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
                    items(Constants.EMOTICON_RAW_ID_LIST.size) { index ->  // 100개의 아이템을 생성
                        // 각 아이템을 그리는 코드를 작성합니다.
                        // 예를 들어, Text Composable을 사용하여 index를 출력할 수 있습니다.
                        IconButton(onClick = { onSelectEmoticon(Constants.EMOTICON_RAW_ID_LIST[index]) }) {
                            Image(
                                painter = painterResource(id = Constants.EMOTICON_RAW_ID_LIST[index]),
                                contentDescription = "감정"
                            )
                        }
                    }
                }
            }
        }
    }
}