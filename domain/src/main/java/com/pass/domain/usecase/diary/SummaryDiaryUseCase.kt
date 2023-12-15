package com.pass.domain.usecase.diary

import com.pass.domain.repository.diary.DiaryRepository
import kotlinx.coroutines.flow.Flow

class SummaryDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(content: String): Flow<String> {
        return repository.summaryDiary(content)
    }
}