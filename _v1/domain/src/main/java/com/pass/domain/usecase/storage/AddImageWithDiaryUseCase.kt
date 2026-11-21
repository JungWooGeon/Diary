package com.pass.domain.usecase.storage

import com.pass.domain.repository.storage.StorageRepository
import javax.inject.Inject

class AddImageWithDiaryUseCase @Inject constructor(private val repository: StorageRepository) {
    suspend operator fun invoke(fileUri: String, pathString: String): String {
        return repository.addImageWithDiary(
            fileUri = fileUri,
            pathString = pathString
        )
    }
}