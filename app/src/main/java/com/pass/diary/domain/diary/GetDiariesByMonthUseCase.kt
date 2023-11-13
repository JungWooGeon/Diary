package com.pass.diary.domain.diary

import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.diary.DiaryRepository

class GetDiariesByMonthUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(year: String, month: String): List<Diary> {
        return repository.getDiariesByMonth(year, month)
    }
}