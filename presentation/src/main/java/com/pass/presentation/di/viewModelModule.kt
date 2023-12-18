package com.pass.presentation.di

import com.pass.presentation.viewmodel.AddDiaryViewModel
import com.pass.presentation.viewmodel.AnalysisViewModel
import com.pass.presentation.viewmodel.CalendarViewModel
import com.pass.presentation.viewmodel.SettingsViewModel
import com.pass.presentation.viewmodel.ThemeViewModel
import com.pass.presentation.viewmodel.TimelineViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        TimelineViewModel(get())
    }

    viewModel {
        AddDiaryViewModel(get(), get(), get(), get(), get())
    }

    viewModel {
        SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    viewModel {
        ThemeViewModel(get())
    }

    viewModel {
        AnalysisViewModel(get())
    }

    viewModel {
        CalendarViewModel(get())
    }
}