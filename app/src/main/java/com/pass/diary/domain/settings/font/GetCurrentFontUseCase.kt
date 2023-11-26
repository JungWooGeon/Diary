package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentFontUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<String> {
        return repository.getCurrentFont()
    }
}