package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentTextSizeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<Float> = repository.getCurrentTextSize()
}