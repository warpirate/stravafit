// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.location.GpsStatusMonitor
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.location.LocationSource
import org.openactivity.core.map.MapLocation
import org.openactivity.model.LatLng
import org.openactivity.recording.EngineInput
import javax.inject.Inject

/**
 * GPS status plus the last fix, for the pre-start map.
 *
 * @property location the latest fix, kept while the status drops back to searching so the map stays put.
 */
data class PreStartFix(val status: GpsStatus, val location: MapLocation?)

/** The device location shown before a recording starts. */
interface PreStartLocation {
    /**
     * Cold stream; collecting it keeps location updates running, so collect only while the pre-start
     * screen is visible.
     */
    fun fixes(): Flow<PreStartFix>
}

/**
 * Real location from `:core:location` (fused provider on gms, platform GPS on foss). One location
 * subscription feeds both the status (through [GpsStatusMonitor]) and the puck.
 */
class DevicePreStartLocation @Inject constructor(private val source: LocationSource) : PreStartLocation {
    override fun fixes(): Flow<PreStartFix> = flow {
        val latest = MutableStateFlow<MapLocation?>(null)
        val tapped = object : LocationSource {
            override fun availability(): LocationAvailability = source.availability()

            override fun locations(): Flow<EngineInput.Location> =
                source.locations().onEach { latest.value = it.toMapLocation() }
        }
        emitAll(
            combine(GpsStatusMonitor(tapped).status(), latest) { status, location ->
                PreStartFix(status, location.takeIf { status.state.allowsLocation })
            }.distinctUntilChanged(),
        )
    }
}

/** Without permission or with location off, an old fix would be misleading. */
private val GpsState.allowsLocation: Boolean
    get() = this == GpsState.SEARCHING || this == GpsState.WEAK || this == GpsState.GOOD

/** Converts an engine fix into the map puck model. */
internal fun EngineInput.Location.toMapLocation(): MapLocation = MapLocation(
    position = LatLng(latitude, longitude),
    accuracyMeters = horizontalAccuracyMeters?.toFloat(),
    bearingDegrees = bearingDegrees?.toFloat(),
    speedMetersPerSecond = speedMps?.toFloat(),
    timeMillis = wallClockMillis,
)
