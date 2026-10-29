// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Turns a [LocationSource] into a [GpsStatus] stream for the pre-start screen.
 *
 * Without permission or with location off it reports that state and re-checks every [recheckInterval], so
 * granting the permission (or switching location on) is picked up without restarting. With fixes flowing,
 * a fix older than [staleAfter] drops the status back to searching.
 */
class GpsStatusMonitor(
    private val source: LocationSource,
    private val staleAfter: Duration = DEFAULT_STALE_AFTER,
    private val recheckInterval: Duration = DEFAULT_RECHECK,
) {
    @Inject
    constructor(source: LocationSource) : this(source, DEFAULT_STALE_AFTER, DEFAULT_RECHECK)

    /** Cold status flow; collecting it keeps location updates running. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun status(): Flow<GpsStatus> = flow {
        while (true) {
            val availability = source.availability()
            if (availability != LocationAvailability.AVAILABLE) {
                emit(GpsStatus.unavailable(availability))
                delay(recheckInterval)
                continue
            }
            source.locations()
                .map { GpsStatus.forAccuracy(it.horizontalAccuracyMeters) }
                .onStart { emit(GpsStatus.SEARCHING) }
                .transformLatest { status ->
                    emit(status)
                    if (status.hasFix) {
                        delay(staleAfter)
                        emit(GpsStatus.SEARCHING)
                    }
                    // Fixes stopped: watch for the permission being revoked or location switched off.
                    while (true) {
                        val now = source.availability()
                        if (now != LocationAvailability.AVAILABLE) emit(GpsStatus.unavailable(now))
                        delay(recheckInterval)
                    }
                }
                .collect { emit(it) }
            // The fix stream ended (permission revoked or provider gone): start over after a pause.
            delay(recheckInterval)
        }
    }.distinctUntilChanged()

    companion object {
        /** Matches the engine's `gpsLostSeconds`. */
        val DEFAULT_STALE_AFTER: Duration = 10.seconds

        val DEFAULT_RECHECK: Duration = 2.seconds
    }
}
