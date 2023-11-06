package com.pass.diary.data.repository.diary

import com.pass.diary.data.entity.Diary

interface DiaryRepository {
    suspend fun getDiariesByMonth(month: String): List<Diary>
    suspend fun addDiary(diary: Diary)
    suspend fun updateDiary(diary: Diary)
    suspend fun deleteDiary(diary: Diary)
}