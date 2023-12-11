package com.pass.diary.domain.diary

import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.diary.DiaryRepository

class GetAllDiariesUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(): List<Diary> {
        return repository.getAllDiaries()
    }
}