package com.pass.presentation.di

import com.pass.domain.usecase.diary.AddDiaryUseCase
import com.pass.domain.usecase.diary.DeleteDiaryUseCase
import com.pass.domain.usecase.diary.GetAllDiariesUseCase
import com.pass.domain.usecase.diary.GetDiariesByMonthUseCase
import com.pass.domain.usecase.diary.SummaryDiaryUseCase
import com.pass.domain.usecase.diary.UpdateDiaryUseCase
import com.pass.domain.usecase.google.BackupDiariesToGoogleDriveUseCase
import com.pass.domain.usecase.google.IsLoggedInUseCase
import com.pass.domain.usecase.google.LogInForGoogleUseCase
import com.pass.domain.usecase.google.LogOutForGoogleUseCase
import com.pass.domain.usecase.google.RestoreDiariesForGoogleDriveUseCase
import com.pass.domain.usecase.settings.font.GetCurrentFontUseCase
import com.pass.domain.usecase.settings.font.GetCurrentTextSizeUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentFontUseCase
import com.pass.domain.usecase.settings.font.UpdateCurrentTextSizeUseCase
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetDiariesByMonthUseCase(get()) }
    factory { AddDiaryUseCase(get()) }
    factory { UpdateDiaryUseCase(get()) }
    factory { DeleteDiaryUseCase(get()) }
    factory { SummaryDiaryUseCase(get()) }
    factory { GetAllDiariesUseCase(get()) }

    factory { GetCurrentTextSizeUseCase(get()) }
    factory { UpdateCurrentTextSizeUseCase(get()) }
    factory { GetCurrentFontUseCase(get()) }
    factory { UpdateCurrentFontUseCase(get()) }

    factory { LogInForGoogleUseCase(get()) }
    factory { LogOutForGoogleUseCase(get()) }
    factory { BackupDiariesToGoogleDriveUseCase(get()) }
    factory { RestoreDiariesForGoogleDriveUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }
}