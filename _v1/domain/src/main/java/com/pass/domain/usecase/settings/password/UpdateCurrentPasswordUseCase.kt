package com.pass.domain.usecase.settings.password

import com.pass.domain.repository.settings.SettingsRepository
import javax.inject.Inject

class UpdateCurrentPasswordUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(password: String) {
        return repository.updateCurrentPassword(password)
    }
}