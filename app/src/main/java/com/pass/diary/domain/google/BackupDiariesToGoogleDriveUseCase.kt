package com.pass.diary.domain.google

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.pass.diary.data.entity.Diary
import com.pass.diary.data.repository.google.GoogleManagerRepository

class BackupDiariesToGoogleDriveUseCase(private val repository: GoogleManagerRepository) {
    suspend operator fun invoke(diaries: List<Diary>) {
        try {
            repository.backupDiariesToGoogleDrive(diaries)
        } catch (e: UserRecoverableAuthIOException) {
            throw e
        }
    }
}