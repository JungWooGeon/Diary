package com.pass.domain.usecase.diary

import com.pass.domain.entity.Diary
import com.pass.domain.repository.diary.DiaryRepository

class GetAllDiariesUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(): List<Diary> {
        return repository.getAllDiaries()
    }
}