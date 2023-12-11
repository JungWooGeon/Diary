package com.pass.diary.domain.google

import android.content.Intent
import com.pass.diary.data.repository.google.GoogleManagerRepository
import kotlinx.coroutines.flow.Flow

class LogInForGoogleUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(activityResultData: Intent): Flow<Boolean> {
        return repository.logInForGoogle(activityResultData)
    }
}