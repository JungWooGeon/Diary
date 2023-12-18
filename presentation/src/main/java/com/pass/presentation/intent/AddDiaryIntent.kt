package com.pass.presentation.intent

import com.pass.domain.model.Diary
import java.time.LocalDate

sealed class AddDiaryIntent {
    data class Initialize(val diary: Diary?) : AddDiaryIntent()
    data object AddDiary : AddDiaryIntent()
    data object DeleteDiary : AddDiaryIntent()

    data class SelectDate(val localDate: LocalDate) : AddDiaryIntent()

    data class WriteTitle(val text: String) : AddDiaryIntent()
    data class WriteContent(val text: String) : AddDiaryIntent()
    data class DeleteEmoticon(val index: Int) : AddDiaryIntent()

    data class UpdateAddDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateEditDialog(val index: Int) : AddDiaryIntent()
    data class UpdateDatePickerDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateRecordDialog(val isOpen: Boolean) : AddDiaryIntent()
    data class UpdateDeleteDialog(val isOpen: Boolean) : AddDiaryIntent()

    data object OnCompleteShowToastDeleteEmoticon : AddDiaryIntent()
    data object OnCompleteShowToastAddEmoticon : AddDiaryIntent()

    data object OnClickSSProgressButton : AddDiaryIntent()
    data class OnSelectEmoticon(val emoticonId: Int) : AddDiaryIntent()
}