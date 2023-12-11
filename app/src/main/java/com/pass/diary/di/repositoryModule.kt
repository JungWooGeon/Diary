package com.pass.diary.di

import com.pass.diary.data.repository.diary.DiaryRepository
import com.pass.diary.data.repository.diary.DiaryRepositoryImpl
import com.pass.diary.data.repository.google.GoogleManagerRepository
import com.pass.diary.data.repository.google.GoogleManagerRepositoryImpl
import com.pass.diary.data.repository.settings.SettingsRepository
import com.pass.diary.data.repository.settings.SettingsRepositoryImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single<DiaryRepository> { DiaryRepositoryImpl(get()) }

    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }

    single<GoogleManagerRepository> { GoogleManagerRepositoryImpl() }
}