// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import kotlinx.coroutines.flow.Flow
import org.openactivity.recording.EngineInput

/** Whether location fixes can be delivered right now. */
enum class LocationAvailability {
    /** Precise location permission is granted and location is switched on. */
    AVAILABLE,

    /** No location permission at all. */
    NO_PERMISSION,

    /** Only approximate location was granted; recording needs precise location. */
    APPROXIMATE_ONLY,

    /** Location (or the GPS provider) is switched off in system settings. */
    DISABLED,
}

/**
 * A stream of location fixes for the recording engine.
 *
 * Implementations: fused provider (gms flavour) and the platform GPS provider (foss flavour, and the gms
 * fallback when Play services are missing). Both request one fix per second at high accuracy.
 */
interface LocationSource {
    /** Checks permission and system settings without starting anything. */
    fun availability(): LocationAvailability

    /**
     * Cold flow of fixes. Collecting it starts location updates; cancelling the collection stops them.
     *
     * Every fix carries `elapsedRealtimeNanos` as its monotonic time. When location is not
     * [LocationAvailability.AVAILABLE] (or the permission is revoked while collecting) the flow completes
     * without error; callers check [availability] to learn why.
     */
    fun locations(): Flow<EngineInput.Location>
}

/** Barometric pressure readings, when the phone has a barometer. */
interface PressureSource {
    /** True when the device has a pressure sensor. */
    val isAvailable: Boolean

    /** Cold flow of readings, at most one per second. Empty when [isAvailable] is false. */
    fun readings(): Flow<EngineInput.Pressure>
}
