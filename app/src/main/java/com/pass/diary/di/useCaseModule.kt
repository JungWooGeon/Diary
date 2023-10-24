package com.pass.diary.di

import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetDiariesByMonthUseCase(get()) }
}