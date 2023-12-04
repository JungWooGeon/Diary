package com.pass.diary.data.remote.service

import com.pass.diary.data.remote.dto.SummaryRequest
import com.pass.diary.data.remote.dto.SummaryResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SummaryService {
    @POST("text-summary/v1/summarize")
    fun getSearchRestaurant(
        @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
        @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
        @Header("Content-Type") contentType: String,
        @Body summaryRequest: SummaryRequest
    ): Call<SummaryResponse>
}