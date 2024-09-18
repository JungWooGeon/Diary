package com.pass.domain.usecase.storage

import com.pass.domain.repository.storage.StorageRepository
import javax.inject.Inject

class DeleteImageWithDiaryUseCase @Inject constructor(private val repository: StorageRepository) {
    suspend operator fun invoke(fileUri: String, pathString: String) {
        return repository.deleteImageWithDiary(
            fileUri = fileUri,
            pathString = pathString
        )
    }
}