// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

import kotlin.time.Duration

/** What closed a lap. */
public enum class LapTrigger {
    /** The athlete pressed the lap button. */
    MANUAL,

    /** An automatic lap after a fixed distance. */
    DISTANCE,

    /** The activity ended. */
    SESSION_END,
}

/**
 * One lap of an activity.
 *
 * @property index zero-based lap number.
 * @property startOffset time from the activity start to the lap start.
 * @property elapsedTime duration of the lap, including pauses.
 * @property movingTime moving time within the lap.
 * @property distance distance covered within the lap.
 * @property elevationGain climbing within the lap.
 * @property trigger what ended the lap.
 */
public data class Lap(
    val index: Int,
    val startOffset: Duration,
    val elapsedTime: Duration,
    val movingTime: Duration,
    val distance: Distance,
    val elevationGain: Distance,
    val trigger: LapTrigger,
) {
    /** Average speed over the lap's moving time. */
    val averageSpeed: Speed get() = Speed.of(distance, movingTime)

    /** True when the lap covers no time (two lap presses at the same instant). Kept, but flagged. */
    val isZeroLength: Boolean get() = elapsedTime == Duration.ZERO
}

/**
 * One auto split (per kilometre or per mile).
 *
 * @property index zero-based split number.
 * @property distance length of the split; equal to the split length except for a final partial split.
 * @property elapsedTime time taken, including any stops inside the split.
 * @property movingTime moving time within the split.
 * @property elevationDelta net elevation change (end minus start), or null without altitude data.
 * @property isPartial true for a final split shorter than the split length.
 */
public data class Split(
    val index: Int,
    val distance: Distance,
    val elapsedTime: Duration,
    val movingTime: Duration,
    val elevationDelta: Distance?,
    val isPartial: Boolean,
) {
    /** Average speed over the split's moving time. */
    val averageSpeed: Speed get() = Speed.of(distance, movingTime)
}

/**
 * Summary of a saved activity, as shown in lists and cards.
 *
 * @property localId client-generated UUID, also the upload idempotency key.
 * @property startEpochMillis UTC start time.
 * @property timeZoneId IANA zone at the start, used to display local times.
 */
public data class ActivitySummary(
    val localId: String,
    val sportType: SportType,
    val name: String,
    val startEpochMillis: Long,
    val timeZoneId: String,
    val distance: Distance,
    val movingTime: Duration,
    val elapsedTime: Duration,
    val elevationGain: Distance,
    val elevationLoss: Distance,
    val maxSpeed: Speed,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Int? = null,
    val averagePower: Double? = null,
    val averageCadence: Double? = null,
    val visibility: Visibility = Visibility.DEFAULT,
    val isCommute: Boolean = false,
    val isIndoor: Boolean = false,
) {
    init {
        require(movingTime <= elapsedTime) { "moving time ($movingTime) exceeds elapsed time ($elapsedTime)" }
    }

    /** Average speed over moving time. */
    val averageSpeed: Speed get() = Speed.of(distance, movingTime)
}
