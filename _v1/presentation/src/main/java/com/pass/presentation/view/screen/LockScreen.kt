package com.pass.presentation.view.screen

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Math.PI

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun LockScreen(
    password: String = "",
    onComplete: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var titleText by remember { mutableStateOf("비밀번호를 입력해주세요") }
    val contentText = remember { mutableStateListOf<String>() }

    var correctState by remember { mutableStateOf(false) }
    val correctContentText = remember { mutableStateListOf<String>() }

    var shouldShake by remember { mutableStateOf(false) }

    val shakeContentBox by animateFloatAsState(
        targetValue = if (shouldShake) 1f else 0f,
        animationSpec = repeatable(
            iterations = 1,
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    val shakePassword: () -> Unit = {
        coroutineScope.launch {
            shouldShake = true
            delay(200)
            shouldShake = false
        }
    }

    val onClickNumber: (String) -> Unit = {
        if (!correctState) {
            if (contentText.size < 3) {
                contentText.add(it)
            } else if (contentText.size == 3) {
                contentText.add(it)

                if (password != "") {
                    // check
                    val current = contentText.joinToString()
                    if (password == current) {
                        onComplete(current)
                    } else {
                        titleText = "비밀번호가 틀렸습니다."
                        contentText.clear()
                        shakePassword()
                    }
                } else {
                    // next confirm
                    // TODO 0.5 초 대기
                    correctState = true
                    titleText = "비밀번호를 한 번 더 입력해주세요."
                }
            }
        } else {
            if (correctContentText.size < 3) {
                correctContentText.add(it)
            } else if (correctContentText.size == 3) {
                correctContentText.add(it)

                if (contentText.joinToString() == correctContentText.joinToString()) {
                    onComplete(correctContentText.joinToString())
                } else {
                    titleText = "비밀번호가 일치하지 않습니다."
                    correctContentText.clear()
                    shakePassword()
                }
            }
        }
    }

    val onClickRemove: () -> Unit = {
        if (!correctState) {
            if (contentText.isNotEmpty()) {
                contentText.removeLast()
            }
        } else {
            if (correctContentText.isNotEmpty()) {
                correctContentText.removeLast()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (kotlin.math.sin(shakeContentBox * 2 * PI) * 10).dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { idx ->
                if (idx > 0) Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if ((!correctState && idx < contentText.size) ||
                                (correctState && idx < correctContentText.size)
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.inversePrimary
                            }
                        )
                        .padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))

        Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
            repeat(3) { idx ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    repeat(3) { rowIdx ->
                        TextButton(onClick = { onClickNumber((idx * 3 + rowIdx + 1).toString()) }) {
                            Text(
                                text = (idx * 3 + rowIdx + 1).toString(),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(Modifier.weight(1f))

                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onClickNumber("0") }
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onClickRemove() }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.Backspace, contentDescription = "remove")
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}