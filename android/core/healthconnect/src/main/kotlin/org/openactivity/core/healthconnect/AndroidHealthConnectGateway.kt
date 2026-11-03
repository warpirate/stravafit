// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * [HealthConnectGateway] over the AndroidX Health Connect client. The client is created only once
 * Health Connect is available, so phones without it never touch it.
 */
@Singleton
public class AndroidHealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectGateway {

    @Volatile private var cached: HealthConnectClient? = null

    /** The client once Health Connect is available; not cached while it is missing, since it can be installed meanwhile. */
    private val client: HealthConnectClient?
        get() = cached ?: if (availability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context).also { cached = it }
        } else {
            null
        }

    override fun availability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return HealthConnectAvailability.NOT_SUPPORTED
        return when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
            else ->
                // Built into Android 14 and later; "unavailable" there means the platform turned it off.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HealthConnectAvailability.NOT_SUPPORTED
                } else {
                    HealthConnectAvailability.NOT_INSTALLED
                }
        }
    }

    override fun backgroundReadAvailable(): Boolean {
        val c = client ?: return false
        return try {
            c.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: RuntimeException) {
            false
        }
    }

    override suspend fun grantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return try {
            c.permissionController.getGrantedPermissions()
        } catch (e: IllegalStateException) {
            // Health Connect was disabled or uninstalled since the check.
            emptySet()
        }
    }

    override suspend fun writeWorkout(workout: HealthWorkout, granted: Set<String>) {
        val c = client ?: throw IllegalStateException("Health Connect is not available")
        if (HealthPermissions.WRITE_EXERCISE !in granted) throw SecurityException("Writing exercise is not allowed")
        val records = HealthRecords.build(workout, granted)
        // The session first: if the extras fail (a permission revoked in between), the workout is still there.
        c.insertRecords(records.take(1))
        records.drop(1).chunked(INSERT_BATCH).forEach { c.insertRecords(it) }
    }

    override suspend fun readWorkouts(from: Instant, to: Instant, granted: Set<String>): List<HealthWorkoutRead> {
        val c = client ?: return emptyList()
        if (HealthPermissions.READ_EXERCISE !in granted) throw SecurityException("Reading exercise is not allowed")
        val sessions = readAll(c, ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))
        return sessions.sortedBy { it.startTime }.map { s ->
            val range = TimeRangeFilter.between(s.startTime, s.endTime)
            val origin = s.metadata.dataOrigin
            val heartRate = if (HealthPermissions.HEART_RATE_READ in granted) {
                readAll(c, HeartRateRecord::class, range)
                    .filter { it.metadata.dataOrigin == origin }
                    .flatMap { r -> r.samples.map { HealthHeartRate(it.time, it.beatsPerMinute) } }
                    .filter { !it.time.isBefore(s.startTime) && !it.time.isAfter(s.endTime) }
            } else {
                emptyList()
            }
            // Only the distance the same app wrote: other apps' records for the same time would count twice.
            val distance = if (HealthPermissions.DISTANCE_READ in granted) {
                readAll(c, DistanceRecord::class, range)
                    .filter { it.metadata.dataOrigin == origin }
                    .sumOf { it.distance.inMeters }
                    .takeIf { it > 0.0 }
            } else {
                null
            }
            val routeResult = s.exerciseRouteResult
            HealthWorkoutRead(
                id = s.metadata.id,
                originPackage = origin.packageName,
                exerciseType = s.exerciseType,
                title = s.title,
                notes = s.notes,
                start = s.startTime,
                end = s.endTime,
                startOffset = s.startZoneOffset,
                route = (routeResult as? ExerciseRouteResult.Data)?.exerciseRoute?.route?.map { l ->
                    HealthLocation(l.time, l.latitude, l.longitude, l.altitude?.inMeters, l.horizontalAccuracy?.inMeters)
                },
                routeConsentRequired = routeResult is ExerciseRouteResult.ConsentRequired,
                heartRate = heartRate,
                distanceMeters = distance,
            )
        }
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val response = c.readRecords(ReadRecordsRequest(type, range, pageSize = PAGE_SIZE, pageToken = token))
            out += response.records
            token = response.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
        return out
    }

    public companion object {
        /** The Health Connect app on Android 9 to 13. */
        public const val PROVIDER: String = "com.google.android.apps.healthdata"

        private const val PAGE_SIZE = 1_000
        private const val INSERT_BATCH = 20
    }
}
