// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneOffset

/** Whether Health Connect can be used on this phone. */
public enum class HealthConnectAvailability {
    /** Android 8 (API 26-27): Health Connect does not run here. */
    NOT_SUPPORTED,

    /** Android 9 to 13 without the Health Connect app. */
    NOT_INSTALLED,

    /** The Health Connect app is too old for this app. */
    UPDATE_REQUIRED,

    /** Ready (built in since Android 14). */
    AVAILABLE,
}

/** A point of a workout's route. Degrees, metres. */
public data class HealthLocation(
    val time: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val horizontalAccuracyMeters: Double? = null,
)

/** One heart-rate sample, beats per minute. */
public data class HealthHeartRate(val time: Instant, val beatsPerMinute: Long)

/**
 * A finished activity to write to Health Connect as an exercise session with its route, distance and
 * heart rate.
 *
 * @property clientId our stable id (the activity uuid); writing it again updates instead of duplicating.
 * @property version raised when the activity changes, so Health Connect takes the newer copy.
 * @property hasGps whether it was recorded outdoors with GPS (a swim with a route is open water).
 */
public data class HealthWorkout(
    val clientId: String,
    val version: Long,
    val sport: SportType,
    val title: String,
    val notes: String?,
    val start: Instant,
    val end: Instant,
    val startOffset: ZoneOffset,
    val endOffset: ZoneOffset,
    val distanceMeters: Double?,
    val route: List<HealthLocation>,
    val heartRate: List<HealthHeartRate>,
    val hasGps: Boolean = route.size >= 2,
)

/**
 * A workout another app wrote to Health Connect.
 *
 * @property id Health Connect's record id.
 * @property originPackage the app that wrote it.
 * @property route the route, or null when there is none or when [routeConsentRequired].
 * @property routeConsentRequired the session has a route, but Health Connect only hands it over after
 *   the user allows it for this one session (other apps' routes are protected that way).
 * @property distanceMeters distance the same app wrote for the session's time, if any.
 */
public data class HealthWorkoutRead(
    val id: String,
    val originPackage: String,
    val exerciseType: Int,
    val title: String?,
    val notes: String?,
    val start: Instant,
    val end: Instant,
    val startOffset: ZoneOffset?,
    val route: List<HealthLocation>?,
    val routeConsentRequired: Boolean,
    val heartRate: List<HealthHeartRate>,
    val distanceMeters: Double?,
)

/**
 * The parts of Health Connect the app uses. [AndroidHealthConnectGateway] talks to the real thing;
 * tests use a fake.
 */
public interface HealthConnectGateway {
    /** Whether Health Connect is installed, current and supported. Cheap; call it on every screen visit. */
    public fun availability(): HealthConnectAvailability

    /** Whether this Health Connect version can grant reading in the background. */
    public fun backgroundReadAvailable(): Boolean

    /** The Health Connect permissions granted to the app; empty when Health Connect is not available. */
    public suspend fun grantedPermissions(): Set<String>

    /**
     * Writes [workout] (session, then distance and heart rate). Parts the user did not allow are left
     * out (the route without its permission, heart rate without its permission).
     *
     * @throws SecurityException when writing exercise is not allowed.
     */
    public suspend fun writeWorkout(workout: HealthWorkout, granted: Set<String>)

    /** Workouts that started in [from, to), oldest first, with their route, heart rate and distance. */
    public suspend fun readWorkouts(from: Instant, to: Instant, granted: Set<String>): List<HealthWorkoutRead>
}
