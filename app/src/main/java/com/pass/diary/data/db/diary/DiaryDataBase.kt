package com.pass.diary.data.db.diary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pass.diary.data.entity.Diary

@Database(entities = [Diary::class], version = 1, exportSchema =false)
abstract class DiaryDataBase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        private const val DIARY_DB = "diary"

        @Volatile
        private var INSTANCE: DiaryDataBase? = null

        fun getInstance(context: Context): DiaryDataBase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                DiaryDataBase::class.java, DIARY_DB
            ).build()
    }
}