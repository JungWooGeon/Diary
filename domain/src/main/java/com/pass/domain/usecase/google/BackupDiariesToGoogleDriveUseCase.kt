package com.pass.domain.usecase.google

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.pass.domain.entity.Diary
import com.pass.domain.repository.google.GoogleManagerRepository

class BackupDiariesToGoogleDriveUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(diaries: List<Diary>) {
        try {
            repository.backupDiariesToGoogleDrive(diaries)
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        } catch (e: Exception) {
            throw e
        }
    }
}