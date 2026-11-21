package com.pass.presentation.view.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomYearDatePicker(
    datePickerYear: Int,
    onDatePickerYearChange: (newYear: Int) -> Unit,
    onDateSelected: (selectedMonth: Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp)) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .wrapContentSize()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "left button",
                            modifier = Modifier.clickable { onDatePickerYearChange(datePickerYear - 1) }
                        )
                        Text(text = datePickerYear.toString(), fontSize = 20.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "right button",
                            modifier = Modifier.clickable { onDatePickerYearChange(datePickerYear + 1) }
                        )
                    }

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(1, 2, 3, 4), onDateSelected)

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(5, 6, 7, 8), onDateSelected)

                    Spacer(modifier = Modifier.size(20.dp))

                    DatePickerRow(listOf(9, 10, 11, 12), onDateSelected)
                }
            }
        }
    }
}

@Composable
fun DatePickerRow(dates: List<Int>, onDateSelected: (selectedDate: Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        dates.forEach { date ->
            Box(modifier = Modifier
                .size(20.dp)
                .clickable { onDateSelected(date) }) {
                Text(text = date.toString(), fontSize = 15.sp)
            }
        }
    }
}