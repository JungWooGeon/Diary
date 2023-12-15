package com.pass.presentation

import android.app.Application
import com.pass.presentation.di.databaseModule
import com.pass.presentation.di.repositoryModule
import com.pass.presentation.di.useCaseModule
import com.pass.presentation.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class DiaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // koin 초기화
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@DiaryApplication)
            modules(
                listOf(
                    databaseModule,
                    repositoryModule,
                    useCaseModule,
                    viewModelModule
                )
            )
        }
    }
}