package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository

class UpdateCurrentFontUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(font: String) {
        return repository.updateCurrentFont(font)
    }
}