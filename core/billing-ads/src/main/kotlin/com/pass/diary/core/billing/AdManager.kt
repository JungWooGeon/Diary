package com.pass.diary.core.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Ad policy (PLAN §4 "광고 조금", ARCHITECTURE §7):
 * - Banner: calendar & report bottom ([AdBanner]). Native: one card in the timeline ([AdNative]).
 * - Rewarded: only "특별 빛 팔레트 하루 써 보기" ([showRewarded]). No interstitials, ever.
 * - Never on write / save / reply / lock / crisis / onboarding: the app sets [LocalAdsAllowed] from a route allowlist,
 *   and every slot renders nothing unless it is true.
 * - Premium ([Billing]) removes every slot entirely.
 */
@Singleton
class AdManager @Inject constructor(val billing: Billing) {
    private val started = AtomicBoolean(false)
    /** Mobile Ads SDK initialized (consent allows ads). Compose-observable. */
    var ready by mutableStateOf(false)
        private set
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    /** Call from Activity.onCreate. Also restores the purchase. Shows the UMP consent form only where required (EEA/UK…). */
    fun gatherConsent(activity: Activity) {
        billing.start()
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(TAG, "consent form: ${it.message}") }
                    startIfAllowed(activity)
                }
            },
            { error -> Log.w(TAG, "consent update: ${error.message}"); startIfAllowed(activity) },
        )
        startIfAllowed(activity) // consent from a previous session
    }

    fun privacyOptionsRequired(context: Context) =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) =
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { it?.let { e -> Log.w(TAG, e.message) } }

    private fun startIfAllowed(activity: Activity) {
        if (!UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) return
        if (!started.compareAndSet(false, true)) return
        val app = activity.applicationContext
        thread {
            MobileAds.initialize(app) {
                Handler(Looper.getMainLooper()).post { ready = true }
            }
        }
    }

    /** Preloads the rewarded ad (call when the palette screen opens). */
    fun loadRewarded(context: Context) {
        if (!ready || billing.isPremium.value || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(context, BuildConfig.REWARD_AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedLoading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { Log.w(TAG, "rewarded: ${error.message}"); rewardedLoading = false }
        })
    }

    val rewardReady get() = rewarded != null

    /**
     * Shows the rewarded ad; [onEarned] runs only if the reward was earned (closing early = nothing). If no ad is ready
     * or it fails to show, [onEarned] runs anyway — a trial is never blocked by ad fill.
     */
    fun showRewarded(activity: Activity?, onEarned: () -> Unit) {
        val ad = rewarded
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null) return onEarned()
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = done()
            override fun onAdFailedToShowFullScreenContent(e: AdError) { earned = true; done() }
            fun done() {
                loadRewarded(activity.applicationContext)
                if (earned) onEarned()
            }
        }
        ad.show(activity) { earned = true }
    }

    companion object { private const val TAG = "Ads" }
}

/** Provided by MainActivity. Null in previews/tests (= no ads). */
val LocalAdManager = staticCompositionLocalOf<AdManager?> { null }

/**
 * True only on screens in the app's ad allowlist (calendar, report). Defaults to false, so a slot placed anywhere else
 * — write, reply, lock, crisis, onboarding — renders nothing.
 */
val LocalAdsAllowed = compositionLocalOf { false }
