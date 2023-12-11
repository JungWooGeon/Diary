package com.pass.diary.di

import com.pass.diary.domain.diary.AddDiaryUseCase
import com.pass.diary.domain.diary.DeleteDiaryUseCase
import com.pass.diary.domain.diary.GetAllDiariesUseCase
import com.pass.diary.domain.diary.GetDiariesByMonthUseCase
import com.pass.diary.domain.diary.SummaryDiaryUseCase
import com.pass.diary.domain.diary.UpdateDiaryUseCase
import com.pass.diary.domain.google.BackupDiariesToGoogleDriveUseCase
import com.pass.diary.domain.google.IsLoggedInUseCase
import com.pass.diary.domain.google.LogInForGoogleUseCase
import com.pass.diary.domain.google.LogOutForGoogleUseCase
import com.pass.diary.domain.google.RestoreDiariesForGoogleDriveUseCase
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