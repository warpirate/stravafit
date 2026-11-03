// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.core.healthconnect.HealthConnectGateway
import org.openactivity.core.healthconnect.HealthConnectStatus
import org.openactivity.core.healthconnect.HealthImport
import org.openactivity.core.healthconnect.HealthWorkoutConverter
import org.openactivity.core.healthconnect.HealthWorkoutRead
import org.openactivity.feature.activitydetail.fileimport.ActivityImportSaver
import org.openactivity.feature.activitydetail.fileimport.ExternalImport
import org.openactivity.feature.activitydetail.fileimport.SaveOutcome
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * How a read from Health Connect went.
 *
 * @property withoutMap workouts imported without their map, because Health Connect hands another app's
 *   route over only after the user allows it for that one workout.
 */
data class SyncResult(
    val imported: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val withoutMap: Int = 0,
    val skipped: Skipped? = null,
) {
    /** Why nothing was read. */
    enum class Skipped { SWITCHED_OFF, NOT_AVAILABLE, NOT_ALLOWED, BACKGROUND_NOT_ALLOWED }
}

/**
 * Imports workouts other apps (Garmin Connect, Samsung Health, Fitbit, ...) wrote to Health Connect.
 *
 * Reads since the last successful read, with a week of overlap because watches sync late; the first
 * read goes back [FIRST_READ] (Health Connect itself only gives an app the 30 days before it was
 * allowed in). Workouts this app wrote are skipped by their data origin, and the rest are stored as
 * `hc:<record id>` with the same duplicate check as the Strava import (same kind of sport starting
 * within a minute), so the run you recorded here and on your watch is there once.
 */
@Singleton
class HealthConnectSync @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val saver: ActivityImportSaver,
    private val preferences: LocalPreferences,
    private val clock: Clock,
    @Named(OWN_PACKAGE) private val ownPackage: String,
) {
    private val mutex = Mutex()

    /**
     * Reads new workouts. [inBackground] is true for the daily job: it then needs the background-read
     * permission, which only newer Health Connect versions have; otherwise the app reads when it opens.
     */
    suspend fun sync(inBackground: Boolean): SyncResult = mutex.withLock {
        if (!preferences.healthReadEnabled.first()) return SyncResult(skipped = SyncResult.Skipped.SWITCHED_OFF)
        val status = HealthConnectStatus(gateway.availability(), gateway.grantedPermissions(), gateway.backgroundReadAvailable())
        when {
            !status.isAvailable -> return SyncResult(skipped = SyncResult.Skipped.NOT_AVAILABLE)
            !status.canRead -> return SyncResult(skipped = SyncResult.Skipped.NOT_ALLOWED)
            inBackground && !status.canReadInBackground -> return SyncResult(skipped = SyncResult.Skipped.BACKGROUND_NOT_ALLOWED)
        }
        val now = clock.instant()
        val from = preferences.healthLastSyncMillis.first()?.let { Instant.ofEpochMilli(it).minus(OVERLAP) } ?: now.minus(FIRST_READ)
        val workouts = gateway.readWorkouts(from, now, status.granted)
        val result = importAll(workouts)
        preferences.setHealthLastSyncMillis(now.toEpochMilli())
        result
    }

    private suspend fun importAll(workouts: List<HealthWorkoutRead>): SyncResult {
        val existing = saver.existingStarts()
        var imported = 0
        var duplicates = 0
        var failed = 0
        var withoutMap = 0
        for (workout in workouts) {
            if (workout.originPackage == ownPackage) continue
            val import = HealthWorkoutConverter.convert(workout) ?: continue
            val details = ExternalImport(
                uuid = UUID_PREFIX + workout.id,
                title = workout.title?.takeIf { it.isNotBlank() },
                description = workout.notes.orEmpty(),
                zone = ActivityImportSaver.zoneFor(workout.startOffset?.totalSeconds),
            )
            val outcome = try {
                when (import) {
                    is HealthImport.Track -> saver.saveExternal(import.activity, details, existing)
                    is HealthImport.Summary -> saver.saveManual(import.activity, details, existing)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveOutcome.Rejected(e.message ?: e.javaClass.simpleName)
            }
            when (outcome) {
                is SaveOutcome.Saved -> {
                    imported++
                    if (workout.routeConsentRequired) withoutMap++
                }
                is SaveOutcome.Duplicate -> duplicates++
                is SaveOutcome.Rejected -> failed++
            }
        }
        return SyncResult(imported, duplicates, failed, withoutMap)
    }

    companion object {
        /** Uuid prefix of activities imported from Health Connect. */
        const val UUID_PREFIX = "hc:"

        /** Hilt name of this app's package, the data origin of what it writes. */
        const val OWN_PACKAGE = "health_connect_own_package"

        val OVERLAP: Duration = Duration.ofDays(7)
        val FIRST_READ: Duration = Duration.ofDays(30)
    }
}

/** Provides this app's package name for [HealthConnectSync]. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object HealthConnectModule {
    @dagger.Provides
    @Named(HealthConnectSync.OWN_PACKAGE)
    fun ownPackage(@ApplicationContext context: Context): String = context.packageName

    @dagger.Provides
    fun gateway(impl: org.openactivity.core.healthconnect.AndroidHealthConnectGateway): HealthConnectGateway = impl
}
