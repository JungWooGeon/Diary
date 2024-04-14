package com.pass.domain.usecase.google

import com.pass.domain.repository.google.GoogleManagerRepository
import javax.inject.Inject

class LogOutForGoogleUseCase @Inject constructor(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke() {
        return repository.logOutForGoogle()
    }
}