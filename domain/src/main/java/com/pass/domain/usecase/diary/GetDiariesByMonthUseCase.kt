package com.pass.domain.usecase.diary

import com.pass.domain.model.Diary
import com.pass.domain.repository.diary.DiaryRepository

class GetDiariesByMonthUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(year: String, month: String): List<Diary> {
        return repository.getDiariesByMonth(year, month)
    }
}