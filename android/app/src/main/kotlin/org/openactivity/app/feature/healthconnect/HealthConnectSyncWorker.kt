// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openactivity.app.data.local.LocalPreferences
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reads Health Connect: daily in the background (where allowed), and once when the app opens. */
class HealthConnectSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val sync = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).healthConnectSync()
        sync.sync(inBackground = inputData.getBoolean(KEY_BACKGROUND, true))
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        // A permission was revoked mid-read: nothing to retry until the user allows it again.
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun healthConnectSync(): HealthConnectSync
    }

    companion object {
        private const val DAILY = "health-connect-daily"
        private const val NOW = "health-connect-now"
        private const val KEY_BACKGROUND = "background"

        /** The daily read; it does nothing on days the background permission is missing. */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(1, TimeUnit.DAYS)
                .setInputData(workDataOf(KEY_BACKGROUND to true))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY)
        }

        /** A read now, while the app is open (so it counts as a foreground read). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                .setInputData(workDataOf(KEY_BACKGROUND to false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/**
 * Starts Health Connect work with the app: the writer that sends new activities, the daily read, and
 * a read whenever the app comes to the front (at most every [OPEN_SYNC_INTERVAL]), which is how phones
 * without background reading stay in sync.
 */
@Singleton
class HealthConnectStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: HealthConnectWriter,
    private val preferences: LocalPreferences,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        // Host-side tests (Robolectric) build the real Application; they must not start background work.
        if (started || Build.FINGERPRINT == ROBOLECTRIC) return
        started = true
        writer.start(scope)
        scope.launch { if (preferences.healthReadEnabled.first()) HealthConnectSyncWorker.scheduleDaily(context) }
    }

    /** The app came to the front. */
    fun onAppOpened() {
        if (Build.FINGERPRINT == ROBOLECTRIC) return
        scope.launch {
            if (!preferences.healthReadEnabled.first()) return@launch
            val last = preferences.healthLastSyncMillis.first() ?: 0L
            if (clock.millis() - last >= OPEN_SYNC_INTERVAL.toMillis()) HealthConnectSyncWorker.runNow(context)
        }
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
        val OPEN_SYNC_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
