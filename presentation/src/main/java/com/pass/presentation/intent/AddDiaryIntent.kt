package com.pass.presentation.intent

import com.pass.domain.model.Diary
import java.time.LocalDate

sealed class AddDiaryIntent {
    data class Initialize(val diary: Diary?) : AddDiaryIntent()
    data class AddDiary(val diary: Diary) : AddDiaryIntent()
    data class UpdateDiary(val diary: Diary) : AddDiaryIntent()
    data class DeleteDiary(val diary: Diary) : AddDiaryIntent()
    data class SummaryContent(val title: String, val content: String) : AddDiaryIntent()

    data class SelectDate(val localDate: LocalDate) : AddDiaryIntent()

    data class WriteTitle(val text: String) : AddDiaryIntent()
    data class WriteContent(val text: String) : AddDiaryIntent()
    data class DeleteEmoticon(val index: Int) : AddDiaryIntent()
    data class UpdateEmoticon(val index: Int, val emoticonId: Int) : AddDiaryIntent()

    data class UpdateAddDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateEditDialog(val index: Int) : AddDiaryIntent()
    data class UpdateDatePickerDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateRecordDialog(val isOpen: Boolean) : AddDiaryIntent()
}