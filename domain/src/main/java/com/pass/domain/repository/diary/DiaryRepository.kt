package com.pass.domain.repository.diary

import com.pass.domain.model.Diary
import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    suspend fun getDiariesByMonth(year: String, month: String): List<Diary>
    suspend fun addDiary(diary: Diary)
    suspend fun updateDiary(diary: Diary)
    suspend fun deleteDiary(diary: Diary)
    suspend fun summaryDiary(content: String): Flow<String>
    suspend fun getAllDiaries(): List<Diary>
}