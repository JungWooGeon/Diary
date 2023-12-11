package com.pass.diary.data.repository.google

import android.content.Intent
import kotlinx.coroutines.flow.Flow

interface GoogleManagerRepository {
    suspend fun logInForGoogleUseCase(activityResultData: Intent): Flow<Boolean>
    suspend fun logOutForGoogleUseCase()
}