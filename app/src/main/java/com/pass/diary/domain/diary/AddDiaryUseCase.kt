package com.pass.diary.domain.diary

import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.diary.DiaryRepository

class AddDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(diary: Diary) {
        return repository.addDiary(diary)
    }
}