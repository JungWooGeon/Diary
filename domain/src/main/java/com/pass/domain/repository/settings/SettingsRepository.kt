package com.pass.domain.repository.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun updateCurrentTextSize(textSize: Float)
    suspend fun getCurrentTextSize(): Flow<Float>
    suspend fun updateCurrentFont(font: String)
    suspend fun getCurrentFont(): Flow<String>
    suspend fun updateCurrentPassword(password: String)
    suspend fun getCurrentPassword(): Flow<String>
}