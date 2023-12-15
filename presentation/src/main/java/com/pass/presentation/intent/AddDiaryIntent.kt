package com.pass.presentation.intent

import com.pass.domain.model.Diary

sealed class AddDiaryIntent {
    data class AddDiary(val diary: Diary) : AddDiaryIntent()
    data class UpdateDiary(val diary: Diary) : AddDiaryIntent()
    data class DeleteDiary(val diary: Diary) : AddDiaryIntent()
    data class SummaryContent(val title: String, val content: String) : AddDiaryIntent()
}