package com.pass.diary

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal object ReviewRules {
    const val DAY_MS = 24 * 60 * 60 * 1000L
    const val COOLDOWN_MS = 60 * DAY_MS
    const val QUIET_START_MS = 3 * DAY_MS
    const val STREAK_DAYS = 7
    const val MIN_ENTRIES = 5
}

/**
 * Play In-App Review only at a happy moment (just closed the reply to a save): a 7-day streak, or a bright/calm/설렘
 * entry once there are 5+ entries. Never in the first 3 days, more than once per 60 days, or after a crisis entry.
 * [lastPromptAt] 0 = never asked.
 */
internal fun shouldAskReview(
    now: Long, firstLaunchAt: Long, lastPromptAt: Long,
    streak: Int, totalEntries: Int, lastEntryPositive: Boolean, lastEntryCrisis: Boolean,
): Boolean = !lastEntryCrisis &&
    now - firstLaunchAt >= ReviewRules.QUIET_START_MS &&
    (lastPromptAt == 0L || now - lastPromptAt >= ReviewRules.COOLDOWN_MS) &&
    (streak >= ReviewRules.STREAK_DAYS || (lastEntryPositive && totalEntries >= ReviewRules.MIN_ENTRIES))

private val POSITIVE = setOf(EmotionGroup.BRIGHT, EmotionGroup.CALM, EmotionGroup.FLUTTER)

/** Top emotion = the strongest (first on a tie). 그리움 sits in 설렘 but isn't a happy moment. */
internal fun Entry.topEmotionPositive(): Boolean =
    emotions.maxByOrNull { it.intensity }?.key?.let { it.group in POSITIVE && it != EmotionKey.MISS } ?: false

/** Thin ReviewManager wrapper. Play decides whether the card actually shows (quota); failures are logged, never thrown. */
internal class InAppReview(private val activity: ComponentActivity) {
    fun launch() {
        activity.lifecycleScope.launch {
            try {
                val m = ReviewManagerFactory.create(activity.applicationContext)
                m.launchReview(activity, m.requestReview())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("InAppReview", "review flow failed", e)
            }
        }
    }
}
