package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentTextSizeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<Float> = repository.getCurrentTextSize()
}