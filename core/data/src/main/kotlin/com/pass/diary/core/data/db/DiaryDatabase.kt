package com.pass.diary.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        EntryEntity::class, EntryEmotionEntity::class, EntryTagEntity::class, PhotoEntity::class, ReplyEntity::class,
        ChatMessageEntity::class, EmotionDefEntity::class, CollectionEntity::class, SyncStateEntity::class, EntryConflictEntity::class,
    ],
    version = 1,
    exportSchema = true, // core/data/schemas
)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun replyDao(): ReplyDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun emotionDefDao(): EmotionDefDao
    abstract fun collectionDao(): CollectionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun entryConflictDao(): EntryConflictDao

    companion object {
        /** v1's DB is "diary" (core:legacy migrates it); v2 never touches that name. */
        const val NAME = "diary_v2.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): DiaryDatabase =
        Room.databaseBuilder(context, DiaryDatabase::class.java, DiaryDatabase.NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) = SCRUB_DELETED.forEach(db::execSQL)
            })
            .build()

    @Provides fun entryDao(db: DiaryDatabase) = db.entryDao()
    @Provides fun replyDao(db: DiaryDatabase) = db.replyDao()
    @Provides fun chatMessageDao(db: DiaryDatabase) = db.chatMessageDao()
    @Provides fun emotionDefDao(db: DiaryDatabase) = db.emotionDefDao()
    @Provides fun collectionDao(db: DiaryDatabase) = db.collectionDao()
    @Provides fun syncStateDao(db: DiaryDatabase) = db.syncStateDao()
    @Provides fun entryConflictDao(db: DiaryDatabase) = db.entryConflictDao()
}
