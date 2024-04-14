package com.pass.data.repository.diary

import android.util.Log
import com.pass.data.db.diary.DiaryDataBase
import com.pass.data.mapper.DiaryMapper
import com.pass.data.remote.dto.DocumentObject
import com.pass.data.remote.dto.OptionObject
import com.pass.data.remote.dto.SummaryRequest
import com.pass.data.remote.dto.SummaryResponse
import com.pass.data.remote.service.SummaryService
import com.pass.domain.entity.Diary
import com.pass.domain.repository.diary.DiaryRepository
import com.sss.data.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepositoryImpl @Inject constructor(private val db: DiaryDataBase) : DiaryRepository {

    private val NAVER_BASE_URL = "https://naveropenapi.apigw.ntruss.com/"

    override suspend fun getDiariesByMonth(year: String, month: String): List<Diary> {
        val diaryEntity = db.diaryDao().getDiariesByMonth(year, month)
        return DiaryMapper.toDomainWithList(diaryEntity)
    }

    override suspend fun addDiary(diary: Diary) {
        return db.diaryDao().addDiary(DiaryMapper.fromDomain(diary))
    }

    override suspend fun updateDiary(diary: Diary) {
        return db.diaryDao().updateDiary(DiaryMapper.fromDomain(diary))
    }

    override suspend fun deleteDiary(diary: Diary) {
        return db.diaryDao().deleteDiary(DiaryMapper.fromDomain(diary))
    }

    override suspend fun summaryDiary(content: String): Flow<String> = callbackFlow {
        val retrofit = Retrofit.Builder()
            .baseUrl(NAVER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(SummaryService::class.java)

        service.getSearchRestaurant(
            clientId = BuildConfig.naver_client_id,
            clientSecret = BuildConfig.naver_client_pw,
            contentType = "application/json",
            summaryRequest = SummaryRequest(
                document = DocumentObject(content = content, title = ""),
                option = OptionObject()
            )
        ).enqueue(object : Callback<SummaryResponse> {
            override fun onResponse(
                call: Call<SummaryResponse>,
                response: Response<SummaryResponse>
            ) {
                if (!response.isSuccessful) {
                    Log.d("오류", "실패: ${response.code()}, ${response.message()}, ${response.errorBody()}, ${response.body()?.error}")
                    trySend("")
                    close()
                    return
                }

                var element = ""

                response.body()?.let { summaryResponse ->
                    Log.d("성공", summaryResponse.summary)
                    element = summaryResponse.summary
                }

                trySend(element)
                close()
            }

            override fun onFailure(call: Call<SummaryResponse>, t: Throwable) {
                Log.d("오류", t.message.toString())
                close(t)
            }
        })

        // close 가 실행될 때 까지 기다림
        awaitClose()
    }

    override suspend fun getAllDiaries(): List<Diary> {
        val diaryEntity = db.diaryDao().getAllDiaries()
        return DiaryMapper.toDomainWithList(diaryEntity)
    }
}