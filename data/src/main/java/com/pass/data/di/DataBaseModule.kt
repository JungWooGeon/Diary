package com.pass.data.di

import android.content.Context
import com.pass.data.db.diary.DiaryDataBase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataBaseModule {

    @Singleton
    @Provides
    fun providesDiaryDataBase(context: Context): DiaryDataBase {
        return DiaryDataBase.getInstance(context)
    }
}