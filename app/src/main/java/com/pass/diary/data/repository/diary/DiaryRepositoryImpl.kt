package com.pass.diary.data.repository.diary

import com.pass.diary.data.db.diary.DiaryDataBase
import com.pass.diary.data.entity.Diary

class DiaryRepositoryImpl(private val db: DiaryDataBase) : DiaryRepository {
    override suspend fun getDiariesByMonth(year: String, month: String): List<Diary> {
        return db.diaryDao().getDiariesByMonth(year, month)
    }

    override suspend fun addDiary(diary: Diary) {
        return db.diaryDao().addDiary(diary)
    }

    override suspend fun updateDiary(diary: Diary) {
        return db.diaryDao().updateDiary(diary)
    }

    override suspend fun deleteDiary(diary: Diary) {
        return db.diaryDao().deleteDiary(diary)
    }
}