package com.pass.diary.presentation.view.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pass.diary.presentation.ui.theme.BoxGray

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