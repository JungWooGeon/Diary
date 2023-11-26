package com.pass.diary.di

import com.pass.diary.domain.diary.AddDiaryUseCase
import com.pass.diary.domain.diary.DeleteDiaryUseCase
import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import com.pass.diary.domain.diary.UpdateDiaryUseCase
import com.pass.diary.domain.settings.font.GetCurrentFontUseCase
import com.pass.diary.domain.settings.font.GetCurrentTextSizeUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentFontUseCase
import com.pass.diary.domain.settings.font.UpdateCurrentTextSizeUseCase
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetDiariesByMonthUseCase(get()) }
    factory { AddDiaryUseCase(get()) }
    factory { UpdateDiaryUseCase(get()) }
    factory { DeleteDiaryUseCase(get()) }

    factory { GetCurrentTextSizeUseCase(get()) }
    factory { UpdateCurrentTextSizeUseCase(get()) }
    factory { GetCurrentFontUseCase(get()) }
    factory { UpdateCurrentFontUseCase(get()) }
}