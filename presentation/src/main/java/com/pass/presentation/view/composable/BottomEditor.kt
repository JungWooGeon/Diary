package com.pass.presentation.view.composable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pass.diary.R
import com.pass.presentation.ui.theme.LineGray

@Composable
fun BottomEditor(
    onOpenRecordDialog: () -> Unit,
    onClickAddImage: () -> Unit,
    onClickSortImage: () -> Unit
) {

    HorizontalDivider(
        modifier = Modifier.padding(top = 10.dp),
        thickness = 1.dp,
        color = LineGray
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(start = 10.dp)
    ) {
        IconButton(onClick = { onClickAddImage() }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_image),
                contentDescription = "이미지 추가"
            )
        }

        IconButton(onClick = { onClickSortImage() }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sort),
                contentDescription = "정렬"
            )
        }

        IconButton(onClick = { onOpenRecordDialog() }) {
            Canvas(modifier = Modifier.size(18.dp)) {
                drawCircle(
                    color = Color.Red,
                    radius = size.minDimension / 2,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }
        }
    }
}