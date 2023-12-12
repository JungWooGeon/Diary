package com.pass.diary.data.repository.google

import android.content.Intent
import com.pass.diary.data.entity.Diary
import kotlinx.coroutines.flow.Flow

interface GoogleManagerRepository {
    suspend fun logInForGoogle(activityResultData: Intent): Flow<Boolean>
    suspend fun logOutForGoogle()
    suspend fun isLoggedIn(): Boolean
    suspend fun restoreDiariesForGoogleDrive(): List<Diary>?
    suspend fun backupDiariesToGoogleDrive(diaries: List<Diary>)
}