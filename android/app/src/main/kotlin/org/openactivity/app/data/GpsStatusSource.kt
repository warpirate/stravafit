// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** A location fix quality sample. [accuracyMeters] is null before the first fix. */
data class GpsFix(val accuracyMeters: Float?)

/** Streams GPS fix quality for the Record pre-start screen. */
interface GpsStatusSource {
    val fixes: Flow<GpsFix>
}

/**
 * Simulated fix that tightens from no fix to about 8 m over a few seconds.
 *
 * Stand-in until `:core:location` (Fused provider in gms, LocationManager in foss) exists; it
 * lets the pre-start UI show every state without touching real location.
 */
class SimulatedGpsStatusSource @Inject constructor() : GpsStatusSource {
    override val fixes: Flow<GpsFix> = flow {
        emit(GpsFix(accuracyMeters = null))
        for (accuracy in listOf(64f, 38f, 24f, 14f, 8f)) {
            delay(STEP_MS)
            emit(GpsFix(accuracy))
        }
    }

    private companion object {
        const val STEP_MS = 1_200L
    }
}
