package com.pass.diary.di

import com.pass.diary.data.repository.diary.DiaryRepository
import com.pass.diary.data.repository.diary.DiaryRepositoryImpl
import org.koin.dsl.module

val repositoryModule = module {
    single<DiaryRepository> { DiaryRepositoryImpl(get()) }
}