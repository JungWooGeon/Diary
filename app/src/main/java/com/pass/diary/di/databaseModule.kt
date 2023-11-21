package com.pass.diary.di

import com.pass.diary.data.db.diary.DiaryDataBase
import org.koin.dsl.module

val databaseModule = module {
    // Room
    single { DiaryDataBase.getInstance(get()) }
}