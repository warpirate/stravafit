// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.healthconnect.HealthConnectGateway
import org.openactivity.core.healthconnect.HealthConnectStatus
import org.openactivity.core.healthconnect.HealthHeartRate
import org.openactivity.core.healthconnect.HealthLocation
import org.openactivity.core.healthconnect.HealthWorkout
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The [HealthWorkout] written for a saved activity. Pure; unit-tested. */
object HealthWorkouts {
    /**
     * [detail] as a Health Connect workout: session from the start for the elapsed time, the route and
     * heart rate from its samples (paused samples too: they lie inside the session), and its distance.
     */
    fun from(detail: ActivityDetail): HealthWorkout {
        val a = detail.activity
        val start = a.startTime
        val end = start.plusMillis(a.elapsedTime.inWholeMilliseconds.coerceAtLeast(1_000))
        val rules = a.timeZone.rules
        return HealthWorkout(
            clientId = a.uuid,
            // Editing an activity does not change its uuid; the version only has to grow when it is written again.
            version = VERSION,
            sport = a.sport,
            title = a.title,
            notes = a.description.ifBlank { null },
            start = start,
            end = end,
            startOffset = rules.getOffset(start),
            endOffset = rules.getOffset(end),
            distanceMeters = a.distanceMeters.takeIf { it > 0.0 },
            route = detail.points.map { p ->
                HealthLocation(Instant.ofEpochMilli(p.epochMillis), p.latitude, p.longitude, p.elevationMeters, p.accuracyMeters)
            },
            heartRate = detail.points.mapNotNull { p ->
                p.heartRate?.let { HealthHeartRate(Instant.ofEpochMilli(p.epochMillis), it.toLong()) }
            },
            hasGps = a.polyline != null || detail.points.size >= 2,
        )
    }

    /** Whether [activity] came from Health Connect itself (never written back). */
    fun cameFromHealthConnect(activity: SavedActivity): Boolean = activity.uuid.startsWith(HealthConnectSync.UUID_PREFIX)

    /**
     * Whether a newly saved [activity] is written straight away: recordings and single imports are; bulk
     * imports from Strava are not (they are history, and "Write past activities" sends them on request).
     */
    fun writesAutomatically(activity: SavedActivity): Boolean =
        !cameFromHealthConnect(activity) && !activity.uuid.startsWith(STRAVA_PREFIX)

    private const val VERSION = 1L
    private const val STRAVA_PREFIX = "strava:"
}

/** How "Write past activities" went. */
data class WriteSummary(val written: Int, val failed: Int)

/**
 * Writes finished activities to Health Connect while the "write" switch is on: every activity saved
 * after the switch was turned on (through a watermark, so one saved while the app was being closed is
 * written on the next start), and on request all earlier ones.
 */
@Singleton
class HealthConnectWriter @Inject constructor(
    private val activities: ActivityRepository,
    private val gateway: HealthConnectGateway,
    private val preferences: LocalPreferences,
) {
    private val mutex = Mutex()

    /** Watches for new activities for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            activities.observeActivities()
                .map { list -> list.maxOfOrNull { it.id } ?: 0L }
                .distinctUntilChanged()
                .collect {
                    try {
                        writeNew()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Health Connect went away or refused; the watermark has not moved, so the next change retries.
                    }
                }
        }
    }

    /** Starts counting from now when the switch is turned on: only activities saved later are written. */
    suspend fun markCurrent() {
        val newest = activities.observeActivities().first().maxOfOrNull { it.id } ?: 0L
        preferences.setHealthWriteWatermark(newest)
    }

    /** Writes every activity newer than the watermark, then moves it. */
    suspend fun writeNew(): Int = mutex.withLock {
        if (!preferences.healthWriteEnabled.first()) return 0
        val status = status()
        if (!status.canWrite) return 0
        val all = activities.observeActivities().first()
        val newest = all.maxOfOrNull { it.id } ?: return 0
        val mark = preferences.healthWriteWatermark.first() ?: run {
            preferences.setHealthWriteWatermark(newest)
            return 0
        }
        var written = 0
        for (activity in all.filter { it.id > mark }.sortedBy { it.id }) {
            if (!HealthWorkouts.writesAutomatically(activity)) continue
            if (write(activity, status.granted)) written++
        }
        preferences.setHealthWriteWatermark(maxOf(mark, newest))
        written
    }

    /** "Write past activities": every stored activity except the ones that came from Health Connect. */
    suspend fun writeAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): WriteSummary = mutex.withLock {
        val status = status()
        if (!status.canWrite) throw SecurityException("Writing to Health Connect is not allowed")
        val all = activities.observeActivities().first().filterNot(HealthWorkouts::cameFromHealthConnect).sortedBy { it.startTime }
        var written = 0
        var failed = 0
        all.forEachIndexed { i, activity ->
            if (write(activity, status.granted)) written++ else failed++
            onProgress(i + 1, all.size)
        }
        WriteSummary(written, failed)
    }

    private suspend fun write(activity: SavedActivity, granted: Set<String>): Boolean {
        val detail = activities.observeActivityDetail(activity.id).first() ?: return false
        return try {
            gateway.writeWorkout(HealthWorkouts.from(detail), granted)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            // One activity Health Connect rejects (odd times, say) must not stop the others.
            false
        }
    }

    private suspend fun status() = HealthConnectStatus(gateway.availability(), gateway.grantedPermissions())
}
