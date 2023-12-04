package com.pass.diary.data.repository.diary

import com.pass.diary.data.entity.Diary
import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    suspend fun getDiariesByMonth(year: String, month: String): List<Diary>
    suspend fun addDiary(diary: Diary)
    suspend fun updateDiary(diary: Diary)
    suspend fun deleteDiary(diary: Diary)
    suspend fun summaryDiary(content: String): Flow<String>
}