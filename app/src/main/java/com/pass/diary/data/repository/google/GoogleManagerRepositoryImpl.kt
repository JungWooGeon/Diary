package com.pass.diary.data.repository.google

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GoogleManagerRepositoryImpl: GoogleManagerRepository {
    override suspend fun logInForGoogleUseCase(activityResultData: Intent): Flow<Boolean> = callbackFlow {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(activityResultData)
                .getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    trySend(task.isSuccessful)
                }
        } catch (e: Exception) {
            trySend(false)
        }

        awaitClose()
    }

    override suspend fun logOutForGoogleUseCase() {
        FirebaseAuth.getInstance().signOut()
    }
}