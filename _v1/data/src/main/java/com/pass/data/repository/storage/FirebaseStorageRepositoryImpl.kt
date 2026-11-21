package com.pass.data.repository.storage

import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import com.pass.data.di.IoDispatcher
import com.pass.domain.repository.storage.StorageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class FirebaseStorageRepositoryImpl @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
): StorageRepository {

    override suspend fun addImageWithDiary(fileUri: String, pathString: String): String = withContext(ioDispatcher) {
        val storage = Firebase.storage
        val uri = URLDecoder.decode(fileUri, StandardCharsets.UTF_8.toString()).toUri()

        // 파일을 비동기적으로 업로드하고, 결과를 기다림
        val uploadTask = storage.reference.child(pathString).putFile(uri).await()
        val downloadUrl = uploadTask.metadata?.reference?.downloadUrl?.await().toString()

        // URL 인코딩
        URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8.toString())
    }

    override suspend fun deleteImageWithDiary(fileUri: String, pathString: String): Unit = withContext(ioDispatcher) {
        val storage = Firebase.storage
        storage.reference.child(pathString).delete().await()
    }
}