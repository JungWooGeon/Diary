package com.pass.domain.usecase.settings.password

import com.pass.domain.repository.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCurrentPasswordUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(): Flow<String> {
        return repository.getCurrentPassword()
    }
}