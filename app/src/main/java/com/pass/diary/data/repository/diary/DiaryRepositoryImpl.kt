package com.pass.diary.data.repository.diary

import com.pass.diary.data.db.diary.DiaryDataBase
import com.pass.diary.data.entity.Diary

class DiaryRepositoryImpl(private val db: DiaryDataBase) : DiaryRepository {
    override suspend fun getDiariesByMonth(month: String): List<Diary> {
        return db.diaryDao().getDiariesByMonth(month)
    }
}