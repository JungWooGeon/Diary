package com.pass.domain.usecase.google

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.pass.domain.entity.Diary
import com.pass.domain.repository.google.GoogleManagerRepository
import javax.inject.Inject

class RestoreDiariesForGoogleDriveUseCase @Inject constructor(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(): List<Diary>? {
        try {
            return repository.restoreDiariesForGoogleDrive()
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }
}