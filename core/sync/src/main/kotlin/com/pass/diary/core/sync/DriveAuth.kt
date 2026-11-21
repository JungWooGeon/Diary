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
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
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
 *  1. `pickAccount(activity, webClientId)` -> email; if `previousAccount(email)` != null ask the user; then `connect(email)`
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

    /** Credential Manager account picker. Changes nothing: check [previousAccount] first, then [connect]. */
    suspend fun pickAccount(activity: Activity, webClientId: String): String = chooseAccount(activity, webClientId)

    /**
     * The account this phone backed up to before (kept across sign-out), if it is not [email]. Non-null = ask the
     * user before [connect]: silently pulling a second person's Drive into this diary would merge two diaries.
     */
    suspend fun previousAccount(email: String): String? = store.meta(META_LAST_ACCOUNT)?.takeUnless { it.equals(email, ignoreCase = true) }

    /** Whether everything was verified on Drive when the previous account was signed out (null = unknown). */
    suspend fun previousAccountWasClean(): Boolean? = store.meta(META_CLEAN_SIGN_OUT)?.toBoolean()

    suspend fun connect(email: String) {
        // sync_state rows without (or from another) account: a DB restored by Auto Backup (sync_meta is not backed up).
        // They may describe another Drive; forgetting them makes everything re-checked and re-uploaded, never skipped.
        if (store.meta(META_ACCOUNT) != email) store.clearSyncState()
        store.setMeta(META_ACCOUNT, email)
        store.setMeta(META_LAST_ACCOUNT, email)
        store.setMeta(META_CLEAN_SIGN_OUT, null)
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
        if (store.meta(META_ACCOUNT) != null) store.setMeta(META_CLEAN_SIGN_OUT, store.pending().isEmpty().toString())
        store.setMeta(META_ACCOUNT, null)
        store.clearSyncState()
    }

    /** 계정·데이터 삭제: take back the drive.appdata grant and forget the sign-in. Best effort (offline = still signed out locally). */
    suspend fun revokeAndSignOut() {
        val account = store.meta(META_ACCOUNT)
        if (account != null) runCatching {
            Identity.getAuthorizationClient(context).revokeAccess(
                RevokeAccessRequest.builder().setAccount(Account(account, "com.google")).setScopes(listOf(Scope(DRIVE_APPDATA))).build(),
            ).await()
        }
        runCatching { CredentialManager.create(context).clearCredentialState(androidx.credentials.ClearCredentialStateRequest()) }
        signOut()
    }

    suspend fun isSignedIn() = store.meta(META_ACCOUNT) != null

    override suspend fun token(): String {
        if (store.meta(META_ACCOUNT) == null) throw SyncAuthException("not signed in")
        val r = try {
            request()
        } catch (e: ApiException) {
            // Account removed from the phone / password changed (invalid_grant) -> needs the UI; network stays retryable.
            if (e.statusCode == CommonStatusCodes.NETWORK_ERROR) throw java.io.IOException(e)
            throw SyncAuthException("authorization failed: ${e.statusCode}")
        }
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
        /** Not "sync.*": survives sign-out and clearSyncState so an account change can be noticed. */
        const val META_LAST_ACCOUNT = "backup.lastAccount"
        private const val META_CLEAN_SIGN_OUT = "backup.cleanSignOut"
    }
}
