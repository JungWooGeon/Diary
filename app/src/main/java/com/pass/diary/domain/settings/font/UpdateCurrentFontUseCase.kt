package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepository

class UpdateCurrentFontUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(font: String) {
        return repository.updateCurrentFont(font)
    }
}