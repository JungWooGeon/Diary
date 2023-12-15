package com.pass.domain.usecase.diary

import com.pass.domain.model.Diary
import com.pass.domain.repository.diary.DiaryRepository

class UpdateDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(diary: Diary) {
        return repository.updateDiary(diary)
    }
}