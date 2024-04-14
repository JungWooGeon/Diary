package com.pass.domain.usecase.settings.font

import com.pass.domain.repository.settings.SettingsRepository
import javax.inject.Inject

class UpdateCurrentFontUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(font: String) {
        return repository.updateCurrentFont(font)
    }
}