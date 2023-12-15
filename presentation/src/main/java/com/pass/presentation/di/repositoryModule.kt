package com.pass.presentation.di

import com.pass.data.repository.diary.DiaryRepositoryImpl
import com.pass.data.repository.google.GoogleManagerRepositoryImpl
import com.pass.data.repository.settings.SettingsRepositoryImpl
import com.pass.domain.repository.diary.DiaryRepository
import com.pass.domain.repository.google.GoogleManagerRepository
import com.pass.domain.repository.settings.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single<DiaryRepository> { DiaryRepositoryImpl(get()) }

    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }

    single<GoogleManagerRepository> { GoogleManagerRepositoryImpl(androidContext()) }
}