package com.pass.diary.presentation.view.activity

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.ui.theme.DiaryTheme
import com.pass.diary.presentation.view.screen.AddDiaryScreen
import com.pass.diary.presentation.view.screen.Constants.INTENT_NAME_DIARY

class AddDiaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TIRAMISU 이상부터는 클래스 타입을 작성하도록 변경되어 분기문 실행 후 deprecated 된 함수 사용
        val diary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(INTENT_NAME_DIARY, Diary::class.java)
        } else {
            intent.getParcelableExtra(INTENT_NAME_DIARY)
        }

        setContent {
            DiaryTheme {
                AddDiaryScreen(diary)
            }
        }
    }
}