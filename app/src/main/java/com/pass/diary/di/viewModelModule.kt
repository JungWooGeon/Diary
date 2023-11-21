package com.pass.diary.di

import com.pass.diary.presentation.viewmodel.AddDiaryViewModel
import com.pass.diary.presentation.viewmodel.MainViewModel
import com.pass.diary.presentation.viewmodel.SettingsViewModel
import com.pass.diary.presentation.viewmodel.TimelineViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        MainViewModel()
    }

    viewModel {
        TimelineViewModel(get())
    }

    viewModel {
        AddDiaryViewModel(get(), get(), get())
    }

    viewModel {
        SettingsViewModel(get(), get())
    }
}