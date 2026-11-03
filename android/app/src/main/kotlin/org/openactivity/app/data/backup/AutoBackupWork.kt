// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.repository.NotificationRepository
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Starts and stops the background job; [WorkManager] keeps it across reboots. */
interface AutoBackupScheduler {
    /** Runs a backup every [frequency] while the battery is not low; [replace] applies a changed frequency. */
    fun schedule(frequency: AutoBackupFrequency, replace: Boolean)

    fun cancel()

    /** Runs one backup now, whatever the battery, even if the screen is left. */
    fun backUpNow()
}

/** [AutoBackupScheduler] over WorkManager: one unique periodic job plus a unique one-off "now" job. */
class WorkManagerAutoBackupScheduler @Inject constructor(@ApplicationContext private val context: Context) : AutoBackupScheduler {
    private val work get() = WorkManager.getInstance(context)

    override fun schedule(frequency: AutoBackupFrequency, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            // The first run waits a little, so turning the feature on does not compete with "Back up now".
            .setInitialDelay(FIRST_RUN_DELAY_HOURS, TimeUnit.HOURS)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
        work.enqueueUniquePeriodicWork(PERIODIC, policy, request)
    }

    override fun cancel() {
        work.cancelUniqueWork(PERIODIC)
    }

    override fun backUpNow() {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(workDataOf(AutoBackupWorker.KEY_MANUAL to true))
            .build()
        // A second tap while one is queued or running changes nothing.
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC = "auto-backup"
        const val NOW = "auto-backup-now"
        const val FIRST_RUN_DELAY_HOURS = 1L
    }
}

/** Adds a failed backup to the notifications inbox, once per kind of failure per day. */
class AutoBackupFailureReporter @Inject constructor(
    private val inbox: NotificationRepository,
    private val clock: Clock,
) {
    suspend fun report(failure: AutoBackupFailure) {
        inbox.add(
            NewNotification(
                type = NotificationType.BACKUP_FAILED,
                title = "Automatic backup failed",
                body = failure.message,
                target = NotificationTarget.SETTINGS,
                dedupeKey = "backup-failed:${failure.name}:${LocalDate.now(clock)}",
            ),
        )
    }
}

/**
 * Runs [AutoBackupRunner] for the periodic job and for "Back up now". A write error worth another try is
 * retried with WorkManager's backoff before it is reported; every other failure is reported at once.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java)
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        return when (val outcome = graph.autoBackupRunner().run()) {
            is AutoBackupOutcome.Success, AutoBackupOutcome.AlreadyRunning -> Result.success()
            is AutoBackupOutcome.Failed -> decide(outcome.failure, manual, runAttemptCount) { graph.autoBackupFailureReporter().report(it) }
        }
    }

    /** Reaches the Hilt graph from a worker WorkManager created itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoBackupEntryPoint {
        fun autoBackupRunner(): AutoBackupRunner

        fun autoBackupFailureReporter(): AutoBackupFailureReporter
    }

    companion object {
        const val KEY_MANUAL = "manual"

        /** Scheduled runs retry a transient write error this many times before reporting it. */
        const val MAX_RETRIES = 2

        /** Retries a transient failure of a scheduled run, otherwise reports it and finishes. */
        internal suspend fun decide(
            failure: AutoBackupFailure,
            manual: Boolean,
            attempt: Int,
            report: suspend (AutoBackupFailure) -> Unit,
        ): Result {
            if (failure.retryable && !manual && attempt < MAX_RETRIES) return Result.retry()
            report(failure)
            return Result.success()
        }
    }
}

/** What Settings shows and changes about automatic backups. */
data class AutoBackupState(
    val config: AutoBackupConfig = AutoBackupConfig(),
    val status: AutoBackupStatus = AutoBackupStatus(),
    val running: Boolean = false,
)

/** Settings' view of automatic backups. */
interface AutoBackupSettings {
    val state: Flow<AutoBackupState>

    /** Turns automatic backups on (a folder must be chosen first) or off. */
    suspend fun setEnabled(enabled: Boolean)

    /** Uses the folder at [tree] from now on, keeping access to it; [enable] also turns backups on. */
    suspend fun chooseFolder(tree: Uri, enable: Boolean)

    suspend fun setFrequency(frequency: AutoBackupFrequency)

    /** Starts one backup now with the same writer. */
    fun backUpNow()
}

/** [AutoBackupSettings] over the store, the runner and WorkManager. */
@Singleton
class LocalAutoBackupSettings @Inject constructor(
    private val store: AutoBackupStore,
    private val runner: AutoBackupRunner,
    private val scheduler: AutoBackupScheduler,
    private val folders: SafBackupFolders,
) : AutoBackupSettings {

    override val state: Flow<AutoBackupState> =
        combine(store.config, store.status, runner.running) { config, status, running -> AutoBackupState(config, status, running) }

    override suspend fun setEnabled(enabled: Boolean) {
        val config = store.config.first()
        if (enabled && config.folderUri == null) return
        store.updateConfig { it.copy(enabled = enabled) }
        if (enabled) scheduler.schedule(config.frequency, replace = false) else scheduler.cancel()
    }

    override suspend fun chooseFolder(tree: Uri, enable: Boolean) {
        val name = folders.takePermission(tree)
        val previous = store.config.first().folderUri
        store.updateConfig { it.copy(folderUri = tree.toString(), folderName = name, enabled = it.enabled || enable) }
        if (previous != null && previous != tree.toString()) folders.releasePermission(Uri.parse(previous))
        val config = store.config.first()
        if (config.enabled) scheduler.schedule(config.frequency, replace = false)
    }

    override suspend fun setFrequency(frequency: AutoBackupFrequency) {
        store.updateConfig { it.copy(frequency = frequency) }
        if (store.config.first().enabled) scheduler.schedule(frequency, replace = true)
    }

    override fun backUpNow() = scheduler.backUpNow()
}

/**
 * Makes the WorkManager job match the stored setup at every start: WorkManager's own records are not in
 * a backup, so after a restore (or if they were cleared) the job is put back, and a stray one is removed.
 */
@Singleton
class AutoBackupStartup @Inject constructor(
    private val store: AutoBackupStore,
    private val scheduler: AutoBackupScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        // Host-side tests (Robolectric) build the real Application; they must not start background work.
        if (Build.FINGERPRINT == ROBOLECTRIC) return
        scope.launch {
            val config = store.config.first()
            if (config.enabled && config.folderUri != null) scheduler.schedule(config.frequency, replace = false) else scheduler.cancel()
        }
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
    }
}
