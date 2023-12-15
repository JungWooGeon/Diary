package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository

class UpdateCurrentTextSizeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(textSize: Float) {
        return repository.updateCurrentTextSize(textSize)
    }
}