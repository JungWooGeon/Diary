package com.pass.domain.usecase.google

import com.pass.domain.repository.google.GoogleManagerRepository

class IsLoggedInUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): Boolean {
        return repository.isLoggedIn()
    }
}