package com.pass.diary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        engine.sync(pushOnly = inputData.getBoolean(KEY_PUSH_ONLY, false))
        Result.success()
    } catch (e: SyncAuthException) {
        Result.failure() // needs the UI; status already says NOT_SIGNED_IN
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_PUSH_ONLY = "pushOnly"
        private const val MAX_ATTEMPTS = 10
    }
}

/** Call sites: after every local save -> [afterSave]; app foreground -> [onAppOpen]; settings "지금 백업" -> [syncNow]. */
object SyncScheduler {
    private const val ONE_SHOT = "sync"
    private const val DAILY = "sync-daily"
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun afterSave(context: Context) = enqueue(context, pushOnly = true)
    fun onAppOpen(context: Context) {
        enqueue(context, pushOnly = false)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS).setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
    }
    fun syncNow(context: Context) = enqueue(context, pushOnly = false)

    fun cancelAll(context: Context) = WorkManager.getInstance(context).run { cancelUniqueWork(ONE_SHOT); cancelUniqueWork(DAILY) }

    private fun enqueue(context: Context, pushOnly: Boolean) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_PUSH_ONLY to pushOnly))
            .build()
        // APPEND_OR_REPLACE: a save during a running sync queues one more pass instead of being dropped.
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds abstract fun driveApi(impl: OkHttpDriveApi): DriveApi
    @Binds abstract fun tokens(impl: DriveAuth): AccessTokenProvider
}
