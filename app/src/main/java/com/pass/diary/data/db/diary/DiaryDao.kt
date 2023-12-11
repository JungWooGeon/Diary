package com.pass.diary.data.db.diary

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pass.diary.data.entity.Diary

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary WHERE month = :month and year = :year")
    fun getDiariesByMonth(year: String, month: String): List<Diary>

    @Insert
    fun addDiary(diary: Diary)

    @Update
    fun updateDiary(diary: Diary)

    @Delete
    fun deleteDiary(diary: Diary)

    @Query("SELECT * FROM diary")
    fun getAllDiaries(): List<Diary>
}