package com.pass.presentation.intent

import com.pass.domain.entity.Diary
import java.time.LocalDate

sealed class AddDiaryIntent {
    data class Initialize(val diary: Diary?) : AddDiaryIntent()
    data class AddDiary(val contentText: String, val titleText: String) : AddDiaryIntent()
    data object DeleteDiary : AddDiaryIntent()

    data class SelectDate(val localDate: LocalDate) : AddDiaryIntent()
    data class DeleteEmoticon(val index: Int) : AddDiaryIntent()

    data class UpdateAddDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateEditDialog(val index: Int) : AddDiaryIntent()
    data class UpdateDatePickerDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateRecordDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateDeleteDialog(val isOpen: Boolean) : AddDiaryIntent()

    data class OnClickSSProgressButton(val contentText: String, val titleText: String) : AddDiaryIntent()
    data class OnSelectEmoticon(val emoticonId: Int) : AddDiaryIntent()
}