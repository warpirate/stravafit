// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.FinishedActivity
import org.openactivity.recording.RecordingConfig
import org.openactivity.recording.RecordingEngine

/** Real engine output for tests: a steady straight-line activity recorded at 1 Hz. */
internal object TestRecordings {
    const val START_WALL_MILLIS: Long = 1_700_000_000_000L // 2023-11-14T22:13:20Z

    fun steady(
        sport: SportType = SportType.RUN,
        seconds: Int = 1200,
        speedMps: Double = 3.0,
        startWallMillis: Long = START_WALL_MILLIS,
        units: UnitSystem = UnitSystem.METRIC,
    ): FinishedActivity {
        val engine = RecordingEngine(RecordingConfig(sport, unitSystem = units))
        val base = 10_000_000_000L
        var position = LatLng(47.37, 8.54)
        engine.process(EngineInput.UserAction(base, Action.START, startWallMillis))
        for (s in 1..seconds) {
            position = Spherical.destination(position, 90.0, speedMps)
            engine.process(
                EngineInput.Location(
                    monotonicNanos = base + s * 1_000_000_000L,
                    wallClockMillis = startWallMillis + s * 1000L,
                    latitude = position.latitude,
                    longitude = position.longitude,
                    horizontalAccuracyMeters = 4.0,
                    altitudeMeters = 400.0 + s * 0.05,
                    verticalAccuracyMeters = 3.0,
                    speedMps = speedMps,
                ),
            )
        }
        engine.process(EngineInput.UserAction(base + (seconds + 1) * 1_000_000_000L, Action.FINISH))
        return checkNotNull(engine.finishedActivity)
    }
}
