package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentTextSizeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<Float> = repository.getCurrentTextSize()
}