// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.core.export.model.ExportStats
import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic test activities (generated geometry, not real recordings). */
object TestActivities {
    val START: Instant = Instant.parse("2026-06-01T06:30:00Z")

    /** A ~5 km run on an oval around a lake, one sample every 2 s, with elevation and heart rate. */
    fun lakeRun(samples: Int = 600): ExportActivity {
        val points = List(samples) { i ->
            val t = 2 * PI * i / samples
            ExportPoint(
                latitude = 47.37 + 0.006 * sin(t),
                longitude = 8.54 + 0.009 * cos(t) - 0.009,
                elevation = 410.0 + 25 * sin(2 * t),
                time = START.plusSeconds(2L * i),
                heartRate = 130 + (i % 40),
                cadence = 85,
                distance = i * 8.3,
            )
        }
        return ExportActivity(
            id = "run-1",
            title = "Lake loop & café",
            sport = SportType.RUN,
            startTime = START,
            points = points,
            stats = ExportStats(
                distanceMeters = 4980.0,
                movingTimeSeconds = 1198,
                elapsedTimeSeconds = 1200,
                elevationGainMeters = 52.0,
                averageHeartRate = 150,
                calories = 320,
            ),
            zone = ZoneOffset.UTC,
        )
    }

    /** An indoor ride: times and heart rate, no positions. */
    fun indoorRide(): ExportActivity = ExportActivity(
        id = "ride-1",
        title = "Trainer",
        sport = SportType.VIRTUAL_RIDE,
        startTime = START,
        points = List(120) { i -> ExportPoint(null, null, time = START.plusSeconds(i.toLong()), heartRate = 120 + i % 10, power = 200) },
        stats = ExportStats(distanceMeters = 0.0, movingTimeSeconds = 119),
        zone = ZoneOffset.UTC,
    )
}
