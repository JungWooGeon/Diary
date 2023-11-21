package com.pass.diary.domain.settings.font

import com.pass.diary.data.repository.settings.SettingsRepository

class UpdateCurrentTextSizeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(textSize: Float) {
        return repository.updateCurrentTextSize(textSize)
    }
}