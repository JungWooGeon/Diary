package com.pass.presentation.di

import com.pass.data.db.diary.DiaryDataBase
import org.koin.dsl.module

val databaseModule = module {
    // Room
    single { DiaryDataBase.getInstance(get()) }
}