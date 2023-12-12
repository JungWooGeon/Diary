package com.pass.diary.domain.google

import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.google.GoogleManagerRepository

class RestoreDiariesForGoogleDriveUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): List<Diary>? {
        return repository.restoreDiariesForGoogleDrive()
    }
}