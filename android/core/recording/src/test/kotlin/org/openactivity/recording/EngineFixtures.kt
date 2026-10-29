// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

/** Noise-free input builders for focused engine tests. */
internal class Feed(
    private var position: LatLng = LatLng(45.0, 7.0),
    private val bearing: Double = 90.0,
    val baseNanos: Long = 10_000_000_000_000L,
    private val baseWall: Long = 1_780_297_200_000L,
    startSeconds: Double = 0.0,
) {
    val inputs = ArrayList<EngineInput>()
    var seconds = startSeconds
        private set

    /** A feed that continues this one's clock from [position] (for example after a jump or a gap). */
    fun continueAt(position: LatLng, afterSeconds: Double = 0.0, bearing: Double = this.bearing) =
        Feed(position, bearing, baseNanos, baseWall, seconds + afterSeconds)

    fun nanos(s: Double): Long = baseNanos + (s * 1e9).toLong()

    fun action(a: Action) = apply { inputs += EngineInput.UserAction(nanos(seconds), a, baseWall + (seconds * 1000).toLong()) }

    /** One fix per second for [duration] seconds at [speed], with ticks in between. */
    fun move(duration: Int, speed: Double, accuracy: Double = 4.0, doppler: Boolean = true) = apply {
        repeat(duration) {
            seconds += 1.0
            position = Spherical.destination(position, bearing, speed)
            inputs += fix(accuracy, if (doppler) speed else null)
            inputs += EngineInput.Tick(nanos(seconds))
        }
    }

    fun fix(accuracy: Double = 4.0, speed: Double? = 0.0, at: LatLng = position, mock: Boolean = false) =
        EngineInput.Location(
            monotonicNanos = nanos(seconds),
            wallClockMillis = baseWall + (seconds * 1000).toLong(),
            latitude = at.latitude,
            longitude = at.longitude,
            horizontalAccuracyMeters = accuracy,
            altitudeMeters = 100.0,
            verticalAccuracyMeters = 3.0,
            speedMps = speed,
            bearingDegrees = bearing,
            isMock = mock,
        )

    fun ticks(duration: Int) = apply {
        repeat(duration) {
            seconds += 1.0
            inputs += EngineInput.Tick(nanos(seconds))
        }
    }

    fun add(input: EngineInput) = apply { inputs += input }

    fun current(): LatLng = position
}

internal fun engine(sport: org.openactivity.model.SportType, autoPause: Boolean? = null): RecordingEngine =
    RecordingEngine(
        if (autoPause == null) RecordingConfig(sport) else RecordingConfig(sport, autoPauseEnabled = autoPause),
    )
