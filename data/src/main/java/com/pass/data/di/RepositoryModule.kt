package com.pass.data.di

import com.pass.data.repository.diary.DiaryRepositoryImpl
import com.pass.data.repository.google.GoogleManagerRepositoryImpl
import com.pass.data.repository.settings.SettingsRepositoryImpl
import com.pass.domain.repository.diary.DiaryRepository
import com.pass.domain.repository.google.GoogleManagerRepository
import com.pass.domain.repository.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindsDiaryRepository(repository: DiaryRepositoryImpl): DiaryRepository

    @Binds
    abstract fun bindsGoogleManagerRepository(repository: GoogleManagerRepositoryImpl): GoogleManagerRepository

    @Binds
    abstract fun bindsSettingsRepository(repository: SettingsRepositoryImpl): SettingsRepository
}