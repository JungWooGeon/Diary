package com.pass.diary.presentation.view.activity

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.pass.diary.presentation.ui.theme.DiaryTheme

class AddDiaryActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        setContent {
            DiaryTheme {
                AddDiaryScreen()
            }
        }
    }
}

@Composable
fun AddDiaryScreen() {

}