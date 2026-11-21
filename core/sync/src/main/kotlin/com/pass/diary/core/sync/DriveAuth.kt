package com.pass.diary.core.sync

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Authentication (who: Credential Manager "Sign in with Google") is separate from authorization
 * (what: AuthorizationClient grants drive.appdata and hands out access tokens). Manual test steps: README.md.
 *
 * UI flow (feature:my 백업·계정):
 *  1. `signIn(activity, webClientId)`  -> account email
 *  2. `authorize()`; if it returns a PendingIntent, launch it with
 *     `ActivityResultContracts.StartIntentSenderForResult` and pass the result Intent to `onConsentResult`.
 *  3. Enqueue `SyncScheduler.syncNow`.
 * Background (worker): `token()` throws [SyncAuthException] if consent is needed -> status NOT_SIGNED_IN.
 */
@Singleton
class DriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SyncLocalStore,
) : AccessTokenProvider {

    suspend fun signIn(activity: Activity, webClientId: String): String {
        val email = chooseAccount(activity, webClientId)
        // sync_state rows without (or from another) account: a DB restored by Auto Backup (sync_meta is not backed up).
        // They may describe another Drive; forgetting them makes everything re-checked and re-uploaded, never skipped.
        if (store.meta(META_ACCOUNT) != email) store.clearSyncState()
        store.setMeta(META_ACCOUNT, email)
        return email
    }

    /** "PIN을 잊었어요": Credential Manager again; true only for the account Drive backup already uses. Changes nothing. */
    suspend fun reauthenticate(activity: Activity, webClientId: String): Boolean {
        val account = store.meta(META_ACCOUNT) ?: return false
        return chooseAccount(activity, webClientId).equals(account, ignoreCase = true)
    }

    private suspend fun chooseAccount(activity: Activity, webClientId: String): String {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest(listOf(option)))
        return GoogleIdTokenCredential.createFrom(result.credential.data).id
    }

    /** null = granted (token cached by Play services); non-null = show this consent screen. */
    suspend fun authorize(): android.app.PendingIntent? {
        val r = request()
        return if (r.hasResolution()) r.pendingIntent else null
    }

    fun onConsentResult(data: Intent?): Boolean =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken != null }.getOrDefault(false)

    /** Also forgets every sync_state row: another account's Drive has none of our files. */
    suspend fun signOut() {
        store.setMeta(META_ACCOUNT, null)
        store.clearSyncState()
    }

    suspend fun isSignedIn() = store.meta(META_ACCOUNT) != null

    override suspend fun token(): String {
        if (store.meta(META_ACCOUNT) == null) throw SyncAuthException("not signed in")
        val r = request()
        if (r.hasResolution()) throw SyncAuthException("drive.appdata consent needed", r.pendingIntent)
        return r.accessToken ?: throw SyncAuthException("no access token")
    }

    override suspend fun invalidate(token: String) = withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(context, token) }

    private suspend fun request(): AuthorizationResult {
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .apply { store.meta(META_ACCOUNT)?.let { setAccount(Account(it, "com.google")) } }
            .build()
        return Identity.getAuthorizationClient(context).authorize(req).await()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val META_ACCOUNT = "sync.account"
    }
}
