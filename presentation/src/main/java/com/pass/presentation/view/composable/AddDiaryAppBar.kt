package com.pass.presentation.view.composable

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pass.diary.R
import java.time.LocalDate

@Composable
fun AddDiaryAppBar(
    context: Context,
    date: LocalDate,
    onAddDiary: () -> Unit,
    onOpenDatePicker: () -> Unit,
    isEdit: Boolean,
    onDeleteDiary: () -> Unit
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

        if (isEdit) {
            IconButton(onClick = { onDeleteDiary() }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete Button",
                    tint = Color.Red
                )
            }
        } else {
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
}