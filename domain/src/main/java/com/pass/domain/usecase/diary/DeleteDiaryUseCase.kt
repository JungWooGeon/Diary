package com.pass.domain.usecase.diary

import com.pass.domain.entity.Diary
import com.pass.domain.repository.diary.DiaryRepository


class DeleteDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(diary: Diary) {
        return repository.deleteDiary(diary)
    }
}