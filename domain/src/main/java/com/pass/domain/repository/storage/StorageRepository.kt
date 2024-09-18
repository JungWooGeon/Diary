package com.pass.domain.repository.storage

interface StorageRepository {
    suspend fun addImageWithDiary(fileUri: String, pathString: String): String
    suspend fun deleteImageWithDiary(fileUri: String, pathString: String)
}