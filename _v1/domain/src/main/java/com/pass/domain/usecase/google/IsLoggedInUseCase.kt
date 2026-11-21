package com.pass.domain.usecase.google

import com.pass.domain.repository.google.GoogleManagerRepository
import javax.inject.Inject

class IsLoggedInUseCase @Inject constructor(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): Boolean {
        return repository.isLoggedIn()
    }
}