package com.pass.diary.presentation.intent

import com.pass.diary.data.entity.Diary

sealed class AddDiaryIntent {
    data class AddDiary(val diary: Diary) : AddDiaryIntent()
}