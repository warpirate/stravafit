// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

/**
 * An event fed to [RecordingEngine].
 *
 * Every event carries a **monotonic** timestamp (on Android: `elapsedRealtimeNanos`). All durations are
 * derived from it, so wall-clock changes (DST, time-zone travel, NTP corrections) never change elapsed or
 * moving time. Wall-clock time appears only on [Location] and [UserAction] and is used solely to label the
 * activity's start time.
 */
public sealed interface EngineInput {
    /** Monotonic timestamp in nanoseconds. */
    public val monotonicNanos: Long

    /**
     * A location fix.
     *
     * @property wallClockMillis UTC wall-clock time of the fix (may jump; never used for durations).
     * @property horizontalAccuracyMeters reported 68 % accuracy radius, or null when unknown.
     * @property altitudeMeters altitude above sea level from the receiver, or null.
     * @property verticalAccuracyMeters reported vertical accuracy, or null.
     * @property speedMps receiver (Doppler) speed, or null when not provided.
     * @property bearingDegrees receiver course over ground, or null.
     * @property isMock true when the platform reports a mock (simulated) location.
     */
    public data class Location(
        override val monotonicNanos: Long,
        val wallClockMillis: Long,
        val latitude: Double,
        val longitude: Double,
        val horizontalAccuracyMeters: Double?,
        val altitudeMeters: Double? = null,
        val verticalAccuracyMeters: Double? = null,
        val speedMps: Double? = null,
        val bearingDegrees: Double? = null,
        val isMock: Boolean = false,
    ) : EngineInput

    /** A barometer reading in hectopascals. */
    public data class Pressure(override val monotonicNanos: Long, val hectopascals: Double) : EngineInput

    /** A heart-rate reading in beats per minute. */
    public data class HeartRate(override val monotonicNanos: Long, val bpm: Int) : EngineInput

    /** A power reading in watts. */
    public data class Power(override val monotonicNanos: Long, val watts: Int) : EngineInput

    /**
     * A cadence reading.
     *
     * @property perMinute crank revolutions per minute, or steps per minute for [CadenceSource.STEPS].
     */
    public data class Cadence(
        override val monotonicNanos: Long,
        val perMinute: Double,
        val source: CadenceSource = CadenceSource.CRANK,
    ) : EngineInput

    /**
     * A user command.
     *
     * @property wallClockMillis wall-clock time when the command was given, if known; the first wall-clock
     *   value the engine sees anchors the activity start time.
     */
    public data class UserAction(
        override val monotonicNanos: Long,
        val action: Action,
        val wallClockMillis: Long? = null,
    ) : EngineInput

    /** A periodic clock tick (the service sends one per second) so timers advance without GPS. */
    public data class Tick(override val monotonicNanos: Long) : EngineInput
}

/** Where a cadence value comes from. */
public enum class CadenceSource {
    /** Bike crank revolutions per minute. */
    CRANK,

    /** Steps per minute (step detector or footpod). */
    STEPS,
}

/** Commands the athlete can give. */
public enum class Action {
    START,
    PAUSE,
    RESUME,
    LAP,
    FINISH,
    DISCARD,
}
