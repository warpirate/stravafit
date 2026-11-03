// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord

/** The Health Connect permissions the app asks for, grouped by what they are for. */
public object HealthPermissions {
    /** Needed to write workouts at all. */
    public val WRITE_EXERCISE: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)

    /** Also asked for when writing; each one left out drops that part of the workout. */
    public val WRITE_EXTRAS: Set<String> = setOf(
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
    )

    /** Needed to read workouts at all. */
    public val READ_EXERCISE: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    /** Also asked for when reading: heart rate and distance of the workouts. */
    public val READ_EXTRAS: Set<String> = setOf(HealthPermission.getReadPermission(HeartRateRecord::class), HealthPermission.getReadPermission(DistanceRecord::class))

    /** Lets the daily job read while the app is closed (newer Health Connect versions only). */
    public val READ_IN_BACKGROUND: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    public val ROUTE_WRITE: String = HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE
    public val HEART_RATE_WRITE: String = HealthPermission.getWritePermission(HeartRateRecord::class)
    public val DISTANCE_WRITE: String = HealthPermission.getWritePermission(DistanceRecord::class)
    public val HEART_RATE_READ: String = HealthPermission.getReadPermission(HeartRateRecord::class)
    public val DISTANCE_READ: String = HealthPermission.getReadPermission(DistanceRecord::class)

    /** What to request when the user turns writing on. */
    public val forWriting: Set<String> = WRITE_EXTRAS + WRITE_EXERCISE

    /** What to request when the user turns reading on; background reading only where it exists. */
    public fun forReading(backgroundAvailable: Boolean): Set<String> =
        READ_EXTRAS + READ_EXERCISE + if (backgroundAvailable) setOf(READ_IN_BACKGROUND) else emptySet()
}

/** One direction of the sync, as the settings screen shows it. */
public enum class SyncDirection { WRITE, READ }

/** What a Health Connect switch shows. */
public enum class SyncSwitchState {
    /** Switched off. */
    OFF,

    /** Switched on and allowed: it works. */
    ON,

    /** Switched on, but Health Connect no longer allows it (the user revoked it there): ask again. */
    NEEDS_PERMISSION,

    /** Health Connect is missing or outdated: the switch cannot be used. */
    UNAVAILABLE,
}

/** How a permission request ended. */
public enum class PermissionRequestOutcome {
    /** Everything asked for was granted. */
    GRANTED,

    /** The essential permission was granted, but some extras (route, heart rate) were not. */
    PARTLY_GRANTED,

    /** The essential permission was not granted (the dialog was dismissed, or Health Connect no longer asks). */
    DENIED,
}

/**
 * What the app may do with Health Connect right now: its [availability] and the [granted] permissions.
 * Pure, so the screen's state machine is tested without Health Connect.
 */
public data class HealthConnectStatus(
    val availability: HealthConnectAvailability,
    val granted: Set<String> = emptySet(),
    val backgroundReadAvailable: Boolean = false,
) {
    public val isAvailable: Boolean get() = availability == HealthConnectAvailability.AVAILABLE

    public val canWrite: Boolean get() = isAvailable && HealthPermissions.WRITE_EXERCISE in granted

    public val canRead: Boolean get() = isAvailable && HealthPermissions.READ_EXERCISE in granted

    /** The daily job may read with the app closed. */
    public val canReadInBackground: Boolean
        get() = canRead && backgroundReadAvailable && HealthPermissions.READ_IN_BACKGROUND in granted

    /** Permissions to request for [direction] that are not granted yet. */
    public fun missing(direction: SyncDirection): Set<String> = when (direction) {
        SyncDirection.WRITE -> HealthPermissions.forWriting
        SyncDirection.READ -> HealthPermissions.forReading(backgroundReadAvailable)
    } - granted

    /** Optional parts of [direction] the user left out ("route", "heart rate", "distance", "background"). */
    public fun leftOut(direction: SyncDirection): List<String> = when (direction) {
        SyncDirection.WRITE -> listOfNotNull(
            "map".takeIf { HealthPermissions.ROUTE_WRITE !in granted },
            "heart rate".takeIf { HealthPermissions.HEART_RATE_WRITE !in granted },
            "distance".takeIf { HealthPermissions.DISTANCE_WRITE !in granted },
        )
        SyncDirection.READ -> listOfNotNull(
            "heart rate".takeIf { HealthPermissions.HEART_RATE_READ !in granted },
            "distance".takeIf { HealthPermissions.DISTANCE_READ !in granted },
        )
    }

    /** The state of the switch for [direction] when the user's setting is [enabled]. */
    public fun switchState(direction: SyncDirection, enabled: Boolean): SyncSwitchState = when {
        !isAvailable -> SyncSwitchState.UNAVAILABLE
        !enabled -> SyncSwitchState.OFF
        direction == SyncDirection.WRITE && !canWrite -> SyncSwitchState.NEEDS_PERMISSION
        direction == SyncDirection.READ && !canRead -> SyncSwitchState.NEEDS_PERMISSION
        else -> SyncSwitchState.ON
    }

    /** How a request for [direction] went, from the permissions granted after it. */
    public fun outcomeOf(direction: SyncDirection, grantedAfter: Set<String>): PermissionRequestOutcome {
        val essential = if (direction == SyncDirection.WRITE) HealthPermissions.WRITE_EXERCISE else HealthPermissions.READ_EXERCISE
        val wanted = copy(granted = emptySet()).missing(direction)
        return when {
            essential !in grantedAfter -> PermissionRequestOutcome.DENIED
            grantedAfter.containsAll(wanted) -> PermissionRequestOutcome.GRANTED
            else -> PermissionRequestOutcome.PARTLY_GRANTED
        }
    }
}
