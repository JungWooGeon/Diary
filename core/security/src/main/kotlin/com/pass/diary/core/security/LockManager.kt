package com.pass.diary.core.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * App lock: PIN (PBKDF2 hash in no-backup prefs; never synced, ARCHITECTURE §2.2) + optional biometrics.
 * Locked at process start when a PIN is set; relocks after the app spent [relockAfterSec] in the background.
 */
@Singleton
class LockManager internal constructor(
    private val context: Context,
    /** Monotonic since boot: changing the wall clock neither skips nor stretches a lockout. */
    private val elapsed: () -> Long,
    private val bootCount: () -> Int,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context, SystemClock::elapsedRealtime,
        { android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1) },
    )

    // Excluded from backup in app/res/xml (backup_rules / data_extraction_rules).
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.contains(KEY_HASH))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private val _biometric = MutableStateFlow(prefs.getBoolean(KEY_BIO, false))
    val biometricEnabled: StateFlow<Boolean> = _biometric.asStateFlow()

    private var backgroundAt: Long? = null

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val hash = withContext(Dispatchers.Default) { PinHasher.encode(pin) }
        prefs.edit { putString(KEY_HASH, hash) }
        _enabled.value = true
    }

    /** "PIN을 잊었어요" after the caller re-authenticated the Drive account: new PIN, attempts cleared, unlocked. */
    suspend fun resetPin(pin: String) {
        setPin(pin)
        clearLockout()
        _locked.value = false
    }

    fun disable() {
        prefs.edit { remove(KEY_HASH); remove(KEY_BIO) }
        clearLockout()
        _enabled.value = false; _biometric.value = false; _locked.value = false
    }

    fun setBiometric(on: Boolean) {
        prefs.edit { putBoolean(KEY_BIO, on) }
        _biometric.value = on
    }

    /**
     * Millis left before another PIN attempt is allowed (0 = now). The deadline lives on [elapsed] of the boot it was set
     * in; after a reboot (that clock restarts) or a deadline further away than the lockout itself, the full wait starts
     * again from now, so rebooting can't skip it either.
     */
    fun lockoutRemaining(): Long {
        val until = prefs.getLong(KEY_UNTIL, -1)
        if (until < 0) return 0
        val now = elapsed()
        val wait = lockoutMillis(prefs.getInt(KEY_FAILS, 0))
        if (prefs.getInt(KEY_BOOT, Int.MIN_VALUE) != bootCount() || until - now > wait) {
            prefs.edit(commit = true) { putLong(KEY_UNTIL, now + wait); putInt(KEY_BOOT, bootCount()) }
            return wait
        }
        return (until - now).coerceAtLeast(0)
    }

    private fun clearLockout() = prefs.edit(commit = true) { putInt(KEY_FAILS, 0); remove(KEY_UNTIL); remove(KEY_BOOT) }

    sealed interface Attempt {
        data object Ok : Attempt
        data class Wrong(val failures: Int) : Attempt
        data class LockedOut(val millis: Long) : Attempt
    }

    suspend fun tryUnlock(pin: String): Attempt {
        lockoutRemaining().takeIf { it > 0 }?.let { return Attempt.LockedOut(it) }
        val hash = prefs.getString(KEY_HASH, null) ?: return Attempt.Ok.also { _locked.value = false }
        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, hash) }
        if (ok) {
            clearLockout()
            _locked.value = false
            return Attempt.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val wait = lockoutMillis(fails)
        // commit: a count still in memory when the process is killed would give free attempts.
        prefs.edit(commit = true) { putInt(KEY_FAILS, fails); if (wait > 0) { putLong(KEY_UNTIL, elapsed() + wait); putInt(KEY_BOOT, bootCount()) } }
        return if (wait > 0) Attempt.LockedOut(wait) else Attempt.Wrong(fails)
    }

    fun canUseBiometric(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the system prompt; success unlocks. The PIN pad stays as the fallback. */
    fun promptBiometric(activity: FragmentActivity, title: String, negative: String) {
        if (!_biometric.value || !canUseBiometric()) return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                clearLockout()
                _locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(negative).setAllowedAuthenticators(BIOMETRIC_WEAK).build(),
        )
    }

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }

    fun onForeground(relockAfterSec: Int) {
        if (_enabled.value && shouldRelock(backgroundAt, SystemClock.elapsedRealtime(), relockAfterSec)) _locked.value = true
        backgroundAt = null
    }

    companion object {
        private const val PREFS = "app_lock"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_BIO = "biometric"
        private const val KEY_FAILS = "failures"
        private const val KEY_UNTIL = "lockout_until_elapsed" // was "lockout_until" (wall clock), now ignored
        private const val KEY_BOOT = "lockout_boot"

        /** 5 wrong PINs → 30s, doubling for each further 5, capped at 10 min. */
        fun lockoutMillis(failures: Int): Long {
            if (failures < 5) return 0
            val steps = (failures - 5) / 5
            return min(30_000L shl min(steps, 10), 10 * 60_000L)
        }

        fun shouldRelock(backgroundAt: Long?, now: Long, afterSec: Int): Boolean =
            backgroundAt != null && now - backgroundAt >= afterSec * 1000L
    }
}
