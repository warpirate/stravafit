// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.location.Location
import androidx.core.location.LocationCompat
import org.openactivity.recording.EngineInput

/**
 * Converts a platform fix into an engine input.
 *
 * The monotonic time is the fix's `elapsedRealtimeNanos`, never the wall clock, so moving and elapsed
 * time are immune to clock changes. Optional values are passed only when the provider reports them.
 */
fun Location.toEngineInput(): EngineInput.Location = EngineInput.Location(
    monotonicNanos = elapsedRealtimeNanos,
    wallClockMillis = time,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
    altitudeMeters = if (hasAltitude()) altitude else null,
    verticalAccuracyMeters = if (LocationCompat.hasVerticalAccuracy(this)) {
        LocationCompat.getVerticalAccuracyMeters(this).toDouble()
    } else {
        null
    },
    speedMps = if (hasSpeed()) speed.toDouble() else null,
    bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
    isMock = LocationCompat.isMock(this),
)
