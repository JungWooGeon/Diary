package com.pass.diary.domain.google

import com.pass.diary.data.repository.google.GoogleManagerRepository

class IsLoggedInUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): Boolean {
        return repository.isLoggedIn()
    }
}