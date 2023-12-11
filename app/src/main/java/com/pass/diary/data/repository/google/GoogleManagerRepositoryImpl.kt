package com.pass.diary.data.repository.google

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.pass.diary.data.entity.Diary
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import java.util.Collections

class GoogleManagerRepositoryImpl(private val context: Context) : GoogleManagerRepository {
    // GoogleAccountCredential: Google Drive REST API 사용을 위한 인증
    private var credential: GoogleAccountCredential? = null

    // Drive: Google Drive 서비스
    private var driveService: Drive? = null

    override suspend fun logInForGoogle(activityResultData: Intent): Flow<Boolean> = callbackFlow {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(activityResultData)
                .getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // 로그인 정보 변경 시 Google Drive 정보 초기화
                        initializeGoogleClients()
                    }
                    trySend(task.isSuccessful)
                }
        } catch (e: Exception) {
            trySend(false)
        }

        awaitClose()
    }

    override suspend fun logOutForGoogle() {
        return FirebaseAuth.getInstance().signOut()
    }

    override suspend fun isLoggedIn(): Boolean {
        // 로그인 상태 확인 전에 클라이언트들 초기화
        initializeGoogleClients()

        // Firebase와 Google SignIn 모두 로그인되어 있는지 확인
        return FirebaseAuth.getInstance().currentUser != null && credential != null
    }

    override suspend fun restoreDiariesForGoogleDrive(): List<Diary> {
        val diaries = mutableListOf<Diary>()

        // 'DiaryBackup' 폴더의 ID를 찾습니다.
        val backupFolderId = driveService?.files()?.list()?.setQ("name = 'DiaryBackup'")?.execute()?.files?.first()?.id
            ?: throw Exception("Failed to find 'DiaryBackup' folder")

        // 'DiaryBackup' 폴더 내의 모든 파일 리스트를 가져옵니다.
        val files = driveService?.files()?.list()?.setQ("'$backupFolderId' in parents")?.execute()?.files

        files?.forEach { file ->
            // 각 파일의 내용을 읽어서 Diary 객체로 변환합니다.
//            val fileId = file.id
//            val outputStream = ByteArrayOutputStream()
//            driveService?.files()?.get(fileId)?.executeMediaAndDownloadTo(outputStream)
//
//            val content = outputStream.toString()

            var temp = file.name.split("|")
            val id = temp[0]
            val title = temp[1]

            temp = file.description.split("|")

            val dayTemp = temp[0].split("/")
            val year = dayTemp[0]
            val month = dayTemp[1]
            val day = dayTemp[2]
            val dayOfWeek = dayTemp[3]

            val emoticonIdTemp = temp[1].split("/")
            val emoticonId1 = if (emoticonIdTemp[0] == "null") { null } else { emoticonIdTemp[0].toInt() }
            val emoticonId2 = if (emoticonIdTemp[1] == "null") { null } else { emoticonIdTemp[1].toInt() }
            val emoticonId3 = if (emoticonIdTemp[2] == "null") { null } else { emoticonIdTemp[2].toInt() }

            val imageUri = if (temp[2] == "null") { null } else { temp[2] }
            val content = temp[3]

            val diary = Diary(
                id = id.toInt(),
                year = year,
                month = month,
                day = day,
                dayOfWeek = dayOfWeek,
                emoticonId1 = emoticonId1,
                emoticonId2 = emoticonId2,
                emoticonId3 = emoticonId3,
                imageUri = imageUri,
                title = title,
                content = content
            )
            diaries.add(diary)
        }

        return diaries
    }


    override suspend fun backupDiariesToGoogleDrive(diaries: List<Diary>) {
        try {
            // 'DiaryBackup' 폴더의 ID를 찾습니다.
            val backupFolderId = driveService?.files()?.list()?.setQ("name = 'DiaryBackup'")?.execute()?.files?.firstOrNull()?.id
                ?: createBackupFolder()

            // 기존 데이터를 임시 폴더로 이동
            val tempFolderId = moveExistingDataToTempFolder(backupFolderId)

            try {
                // 새 데이터를 Google Drive에 업로드
                diaries.forEach { diary ->
                    val emoticonId1 = if (diary.emoticonId1 == null) { "null" } else { diary.emoticonId1 }
                    val emoticonId2 = if (diary.emoticonId2 == null) { "null" } else { diary.emoticonId2 }
                    val emoticonId3 = if (diary.emoticonId3 == null) { "null" } else { diary.emoticonId3 }
                    val imageUri = if (diary.imageUri == null) { "null" } else { diary.imageUri }

                    val fileMetadata = com.google.api.services.drive.model.File()
                    fileMetadata.name = diary.id.toString() + "|" + diary.title
                    fileMetadata.description = diary.year + "/" + diary.month + "/" + diary.day + "/" + diary.dayOfWeek + "|" + emoticonId1 + "/" + emoticonId2 + "/" + emoticonId3 + "|" + imageUri + "|" + diary.content
                    fileMetadata.parents = Collections.singletonList(backupFolderId)

                    // Diary의 내용을 ByteArray로 변환하여 MediaContent 생성
                    // val mediaContent = ByteArrayContent.fromString(null, diary.content)
                    // val request = driveService?.files()?.create(fileMetadata, mediaContent)

                    val request = driveService?.files()?.create(fileMetadata)
                    request?.fields = "id"
                    request?.execute()
                }

                // 새 데이터 저장에 성공하면 기존 데이터를 삭제
                deleteDataInTempFolder(tempFolderId)
            } catch (e: Exception) {
                // 새 데이터 저장에 실패하면 기존 데이터를 복원
                restoreDataFromTempFolder(tempFolderId)
                println("Error: ${e.message}")
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.d("오류", e.intent.type.toString())
            throw e
        }
    }

    // 'DiaryBackup' 폴더를 생성하고 그 ID를 반환하는 함수입니다.
    private fun createBackupFolder(): String {
        try {
            val folderMetadata = com.google.api.services.drive.model.File()
            folderMetadata.name = "DiaryBackup"
            folderMetadata.mimeType = "application/vnd.google-apps.folder"

            val folder = driveService?.files()?.create(folderMetadata)?.setFields("id")?.execute()
            return folder?.id ?: throw Exception("Failed to create 'DiaryBackup' folder")
        } catch (e: UserRecoverableAuthIOException) {
            Log.d("오류", e.intent.action.toString())
            return e.message.toString()
        } catch (e: Exception) {
            Log.d("오류", e.message.toString())
            return e.message.toString()
        }
    }


    // 기존 데이터를 임시 폴더로 이동하는 함수
    private fun moveExistingDataToTempFolder(backupFolderId: String): String {
        try {
            // 임시 폴더 생성
            val tempFolderMetadata = com.google.api.services.drive.model.File()
            tempFolderMetadata.name = "temp"
            tempFolderMetadata.mimeType = "application/vnd.google-apps.folder"

            if(driveService == null) {
                Log.d("여기서 오류", "driveService null")
            }

            val tempFolder = driveService?.files()?.create(tempFolderMetadata)?.setFields("id")?.execute()
            val tempFolderId = tempFolder?.id ?: throw Exception("Failed to create temp folder")

            // 'DiaryBackup' 폴더 내의 기존 데이터를 임시 폴더로 이동
            val files = driveService?.files()?.list()?.setQ("'$backupFolderId' in parents")?.execute()?.files
            files?.forEach { file ->
                val updatedFile = com.google.api.services.drive.model.File()
                updatedFile.parents = Collections.singletonList(tempFolderId)
                driveService?.files()?.update(file.id, updatedFile)?.execute()
            }

            return tempFolderId
        } catch (e: Exception) {
            Log.d("오류", e.message.toString())
            return e.message.toString()
        }
    }

    // 임시 폴더에서 기존 데이터를 복원하는 함수
    private fun restoreDataFromTempFolder(tempFolderId: String) {
        // 임시 폴더의 파일들을 원래 위치로 이동
        val files =
            driveService?.files()?.list()?.setQ("'$tempFolderId' in parents")?.execute()?.files
        files?.forEach { file ->
            val updatedFile = com.google.api.services.drive.model.File()
            updatedFile.parents = Collections.singletonList("root")
            driveService?.files()?.update(file.id, updatedFile)?.execute()
        }
    }

    // 임시 폴더의 데이터를 삭제하는 함수
    private fun deleteDataInTempFolder(tempFolderId: String) {
        // 임시 폴더의 파일들 삭제
        val files =
            driveService?.files()?.list()?.setQ("'$tempFolderId' in parents")?.execute()?.files
        files?.forEach { file ->
            driveService?.files()?.delete(file.id)?.execute()
        }

        // 임시 폴더 삭제
        driveService?.files()?.delete(tempFolderId)?.execute()
    }

    // 로그인 상태 확인 및 필요한 클라이언트 초기화
    private fun initializeGoogleClients() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && FirebaseAuth.getInstance().currentUser != null) {
            credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).setSelectedAccount(account.account)

            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory(),
                credential
            ).build()
        }
    }
}