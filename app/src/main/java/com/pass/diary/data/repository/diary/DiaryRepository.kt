package com.pass.diary.data.repository.diary

import com.pass.diary.data.entity.Diary

interface DiaryRepository {
    suspend fun getDiariesByMonth(month: String): List<Diary>
}