package com.pass.data.di

import com.pass.data.remote.service.SummaryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val NAVER_BASE_URL = "https://naveropenapi.apigw.ntruss.com/"

@Module
@InstallIn(SingletonComponent::class)
class RetrofitModule {

    @Provides
    fun providesRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NAVER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    fun providesSummaryService(retrofit: Retrofit): SummaryService {
        return retrofit.create(SummaryService::class.java)
    }
}