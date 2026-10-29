// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.core.location.GpsStatusMonitor
import javax.inject.Inject

/** A location fix quality sample. [accuracyMeters] is null while there is no (fresh) fix. */
data class GpsFix(val accuracyMeters: Float?)

/** Streams GPS fix quality for the Record pre-start screen. */
interface GpsStatusSource {
    val fixes: Flow<GpsFix>
}

/**
 * Real GPS status from `:core:location` (fused provider in gms, platform GPS in foss). Without location
 * permission, or with location switched off, it reports no fix.
 */
class DeviceGpsStatusSource @Inject constructor(monitor: GpsStatusMonitor) : GpsStatusSource {
    override val fixes: Flow<GpsFix> = monitor.status().map { status ->
        GpsFix(if (status.hasFix) status.accuracyMeters?.toFloat() else null)
    }
}
