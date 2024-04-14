package com.pass.presentation.view.activity

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pass.domain.entity.Diary
import com.pass.presentation.ui.theme.DiaryTheme
import com.pass.presentation.view.screen.AddDiaryScreen
import com.pass.presentation.view.screen.Constants.INTENT_NAME_DIARY
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
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