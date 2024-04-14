package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository
import javax.inject.Inject

class UpdateCurrentTextSizeUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(textSize: Float) {
        return repository.updateCurrentTextSize(textSize)
    }
}