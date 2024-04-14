package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentFontUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<String> {
        return repository.getCurrentFont()
    }
}