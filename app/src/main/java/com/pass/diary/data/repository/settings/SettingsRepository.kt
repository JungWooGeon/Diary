package com.pass.diary.data.repository.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun updateCurrentTextSize(textSize: Float)
    suspend fun getCurrentTextSize(): Flow<Float>
}