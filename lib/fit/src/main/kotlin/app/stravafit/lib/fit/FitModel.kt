// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.fit

import java.time.Instant
import java.time.LocalDateTime

/**
 * A FIT activity file in SI units. Enumerated values ([FitSport], [FitLapTrigger], ...) are kept as
 * raw `Int` codes so unknown values survive a round trip.
 *
 * FIT stores whole-second timestamps and fixed-point values, so encoding rounds: positions to
 * semicircles (~1 cm), altitude to 0.2 m, distance to 1 cm, speed to 1 mm/s, durations to 1 ms.
 */
public data class FitActivity(
    val fileId: FitFileId,
    val records: List<FitRecord>,
    val events: List<FitEvent> = emptyList(),
    val laps: List<FitLap> = emptyList(),
    val sessions: List<FitSession> = emptyList(),
    val activity: FitActivitySummary? = null,
)

/**
 * The `file_id` message: identifies the file and the creating device. Manufacturer + serial number
 * + creation time make a practical de-duplication key.
 */
public data class FitFileId(
    val type: Int = FitFileType.ACTIVITY,
    val manufacturer: Int = FitManufacturer.DEVELOPMENT,
    val product: Int? = null,
    val serialNumber: Long? = null,
    val timeCreated: Instant? = null,
    val productName: String? = null,
)

/** One `record` sample. Units: degrees, metres, m/s, bpm, rpm, watts, °C. */
public data class FitRecord(
    val timestamp: Instant,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val distance: Double? = null,
    val speed: Double? = null,
    val power: Int? = null,
    val temperature: Int? = null,
) {
    init {
        require((latitude == null) == (longitude == null)) { "latitude and longitude must be set together" }
    }
}

/** An `event` message, e.g. timer start/stop. See [FitEventCode] and [FitEventType]. */
public data class FitEvent(
    val timestamp: Instant,
    val event: Int,
    val eventType: Int,
    val data: Long? = null,
    val eventGroup: Int? = null,
)

/** A `lap` summary; [timestamp] is the lap end. Durations in seconds. */
public data class FitLap(
    val timestamp: Instant,
    val startTime: Instant,
    val totalElapsedTime: Double,
    val totalTimerTime: Double,
    val totalDistance: Double? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val totalCalories: Int? = null,
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val totalAscent: Int? = null,
    val totalDescent: Int? = null,
    val intensity: Int? = null,
    val lapTrigger: Int? = null,
    val sport: Int? = null,
)

/** A `session` summary; [timestamp] is the session end. Multisport files have several sessions. */
public data class FitSession(
    val timestamp: Instant,
    val startTime: Instant,
    val sport: Int,
    val subSport: Int = FitSubSport.GENERIC,
    val totalElapsedTime: Double,
    val totalTimerTime: Double,
    val totalDistance: Double? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val totalCalories: Int? = null,
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgCadence: Int? = null,
    val maxCadence: Int? = null,
    val avgPower: Int? = null,
    val maxPower: Int? = null,
    val totalAscent: Int? = null,
    val totalDescent: Int? = null,
    val firstLapIndex: Int? = null,
    val numLaps: Int? = null,
)

/**
 * The `activity` message. [localTimestamp] is the wall-clock time at [timestamp]; their difference
 * is the UTC offset where the activity was recorded.
 */
public data class FitActivitySummary(
    val timestamp: Instant,
    val totalTimerTime: Double,
    val numSessions: Int,
    val type: Int = FitActivityType.MANUAL,
    val localTimestamp: LocalDateTime? = null,
)
