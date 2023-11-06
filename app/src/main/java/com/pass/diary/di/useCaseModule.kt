package com.pass.diary.di

import com.pass.diary.domain.diary.AddDiaryUseCase
import com.pass.diary.domain.diary.DeleteDiaryUseCase
import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import com.pass.diary.domain.diary.UpdateDiaryUseCase
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetDiariesByMonthUseCase(get()) }
    factory { AddDiaryUseCase(get()) }
    factory { UpdateDiaryUseCase(get()) }
    factory { DeleteDiaryUseCase(get()) }
}