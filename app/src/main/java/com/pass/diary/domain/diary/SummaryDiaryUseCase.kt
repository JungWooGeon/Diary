package com.pass.diary.domain.diary

import com.pass.diary.data.repository.diary.DiaryRepository
import kotlinx.coroutines.flow.Flow

class SummaryDiaryUseCase(private val repository: DiaryRepository) {
    suspend operator fun invoke(content: String): Flow<String> {
        return repository.summaryDiary(content)
    }
}