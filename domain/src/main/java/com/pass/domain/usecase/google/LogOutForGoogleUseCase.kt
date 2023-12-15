package com.pass.domain.usecase.google

import com.pass.domain.repository.google.GoogleManagerRepository

class LogOutForGoogleUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke() {
        return repository.logOutForGoogle()
    }
}