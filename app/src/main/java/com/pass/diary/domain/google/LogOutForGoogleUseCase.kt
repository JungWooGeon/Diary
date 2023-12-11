package com.pass.diary.domain.google

import com.pass.diary.data.repository.google.GoogleManagerRepository

class LogOutForGoogleUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke() {
        return repository.logOutForGoogle()
    }
}