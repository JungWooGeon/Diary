package com.pass.diary

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pass.diary.core.data.db.EntryConflictDao
import com.pass.diary.core.data.db.EntryDao
import com.pass.diary.core.legacy.LegacyMigrationResult
import com.pass.diary.core.legacy.LegacyMigrator
import com.pass.diary.core.sync.DriveAuth
import com.pass.diary.core.sync.SyncLocalStore
import com.pass.diary.core.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class DiaryApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

/**
 * Runs once per process before any screen touches Room: v1 migration (ARCHITECTURE §8), then housekeeping and
 * "back up after every save". The splash stays up until [run] returns.
 */
@Singleton
class Startup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val migrator: LegacyMigrator,
    private val entries: EntryDao,
    private val repository: com.pass.diary.core.data.EntryRepository,
    private val conflicts: EntryConflictDao,
    private val syncStore: SyncLocalStore,
    private val settings: com.pass.diary.core.data.SettingsRepository,
    private val billing: com.pass.diary.core.billing.Billing,
    private val embeddings: EmbeddingStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var done = false

    /** Returns the migration result the first time something was migrated, else null. */
    suspend fun run(): LegacyMigrationResult? = mutex.withLock {
        if (done) return null
        val result = runCatching { migrator.migrateIfNeeded() }.onFailure { Log.e(TAG, "v1 migration failed", it) }.getOrNull()
        // v1 stored the lock password in plain text; v2 never reads it (new PIN required) -> remove it.
        File(context.filesDir, "datastore/password_settings.preferences_pb").delete()
        done = true
        embeddings.start()
        scope.launch {
            // The on-device LLM is gone (versionCode 202): free a model downloaded by an earlier build (~2.6 GB).
            File(context.filesDir, "models").deleteRecursively()
            File(context.dataDir, "shared_prefs/oni_model.xml").delete()
            runCatching { androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("oni-model") }
            val now = System.currentTimeMillis()
            runCatching { repository.purgeTrash(now); entries.purgeDeleted(now - THIRTY_DAYS); conflicts.purge(now) }
            if (syncStore.meta(DriveAuth.META_ACCOUNT) != null) SyncScheduler.onAppOpen(context)
            runCatching { com.pass.diary.core.reminder.Reminders.reschedule(context) } // force-stop / restore drop alarms
            backUpAfterSaves()
        }
        result?.takeIf { it.migrated > 0 }
    }

    @OptIn(FlowPreview::class)
    private suspend fun backUpAfterSaves() {
        // settings.json / entitlement.json changes ride the same "back up after save" path.
        val docs = kotlinx.coroutines.flow.combine(settings.settings, billing.isPremium) { s, p -> s.updatedAt to p }
        kotlinx.coroutines.flow.merge(entries.observeLastChange().distinctUntilChanged().drop(1), docs.distinctUntilChanged().drop(1))
            .debounce(2_000).collect {
            runCatching { updateWidgets(context) }
            if (syncStore.meta(DriveAuth.META_ACCOUNT) != null) SyncScheduler.afterSave(context)
        }
    }

    private companion object {
        const val TAG = "Startup"
        const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
    }
}
