package com.pass.domain.usecase.google

import com.pass.domain.model.Diary
import com.pass.domain.repository.google.GoogleManagerRepository

class RestoreDiariesForGoogleDriveUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): List<Diary>? {
        return repository.restoreDiariesForGoogleDrive()
    }
}