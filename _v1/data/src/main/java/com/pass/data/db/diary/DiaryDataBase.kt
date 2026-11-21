package com.pass.data.db.diary

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pass.data.db.entity.DiaryEntity

@Database(entities = [DiaryEntity::class], version = 2, exportSchema = false)
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
            ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Diary 테이블을 복사
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `DiaryEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `year` TEXT NOT NULL, `month` TEXT NOT NULL, `day` TEXT NOT NULL, `dayOfWeek` TEXT NOT NULL, `emoticonId1` INTEGER, `emoticonId2` INTEGER, `emoticonId3` INTEGER, `imageUri` TEXT, `title` TEXT NOT NULL, `content` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `DiaryEntity` (`id`, `year`, `month`, `day`, `dayOfWeek`, `emoticonId1`, `emoticonId2`, `emoticonId3`, `imageUri`, `title`, `content`) SELECT `id`, `year`, `month`, `day`, `dayOfWeek`, `emoticonId1`, `emoticonId2`, `emoticonId3`, `imageUri`, `title`, `content` FROM `Diary`"
                )
                // 기존 Diary 테이블 삭제
                db.execSQL("DROP TABLE `Diary`")
            }
        }
    }
}