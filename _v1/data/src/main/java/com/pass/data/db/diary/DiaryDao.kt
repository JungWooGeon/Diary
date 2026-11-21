package com.pass.data.db.diary

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pass.data.db.entity.DiaryEntity

@Dao
interface DiaryDao {
    @Query("SELECT * FROM DiaryEntity WHERE month = :month and year = :year")
    fun getDiariesByMonth(year: String, month: String): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addDiary(diary: DiaryEntity)

    @Update
    fun updateDiary(diary: DiaryEntity)

    @Delete
    fun deleteDiary(diary: DiaryEntity)

    @Query("SELECT * FROM DiaryEntity")
    fun getAllDiaries(): List<DiaryEntity>
}