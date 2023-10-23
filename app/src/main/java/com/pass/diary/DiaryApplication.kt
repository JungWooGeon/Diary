package com.pass.diary

import android.app.Application
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import com.pass.diary.di.databaseModule
import com.pass.diary.di.repositoryModule
import com.pass.diary.di.useCaseModule
import com.pass.diary.di.viewModelModule
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

        // logger 초기화
        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .showThreadInfo(false)
            .methodCount(2)
            .methodOffset(0)
            .tag("TEST_LOGGER")
            .build()

        Logger.addLogAdapter(AndroidLogAdapter(formatStrategy))
    }
}