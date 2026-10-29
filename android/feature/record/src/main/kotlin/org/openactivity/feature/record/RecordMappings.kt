// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.map.MapLocation
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.format.LiveMetrics
import org.openactivity.feature.record.location.PreStartFix
import org.openactivity.model.LatLng
import org.openactivity.model.SportType

/** A pre-start fix plus whether the slow-fix hint is due. */
internal data class GpsReading(val fix: PreStartFix, val slowFixHint: Boolean) {
    companion object {
        /**
         * Adds the slow-fix hint to [fixes]: it turns on after [hintAfterMs] of continuous searching and off
         * as soon as a fix (or a different problem, such as missing permission) arrives.
         */
        fun watch(fixes: Flow<PreStartFix>, hintAfterMs: Long): Flow<GpsReading> = channelFlow {
            var hintJob: Job? = null
            var hint = false
            var latest: PreStartFix? = null
            launch {
                fixes.collect { fix ->
                    latest = fix
                    if (fix.status.state == GpsState.SEARCHING) {
                        if (hintJob == null) {
                            hintJob = launch {
                                delay(hintAfterMs)
                                hint = true
                                latest?.let { send(GpsReading(it, true)) }
                            }
                        }
                    } else {
                        hintJob?.cancel()
                        hintJob = null
                        hint = false
                    }
                    send(GpsReading(fix, hint))
                }
            }
            awaitClose()
        }
    }
}

/** Maps location status onto the pill. */
internal object GpsMapping {
    /** Before recording: [status] is null until the first reading. */
    fun preStart(sport: SportType, status: GpsStatus?): GpsUi = when {
        !sport.usesGps -> GpsUi(GpsIndicator.Indoor)
        status == null -> GpsUi(GpsIndicator.Acquiring)
        else -> fromStatus(status)
    }

    /** While recording. */
    fun recording(sport: SportType, status: GpsStatus): GpsUi =
        if (sport.usesGps) fromStatus(status) else GpsUi(GpsIndicator.Indoor)

    private fun fromStatus(status: GpsStatus): GpsUi {
        val accuracy = status.accuracyMeters?.toFloat()
        return when (status.state) {
            GpsState.NO_PERMISSION -> GpsUi(GpsIndicator.NoPermission)
            GpsState.APPROXIMATE_ONLY -> GpsUi(GpsIndicator.ApproximateOnly)
            GpsState.DISABLED -> GpsUi(GpsIndicator.LocationOff)
            GpsState.SEARCHING -> GpsUi(GpsIndicator.Acquiring)
            GpsState.WEAK -> GpsUi(GpsIndicator.Weak, accuracy)
            GpsState.GOOD -> GpsUi(GpsIndicator.Ready, accuracy)
        }
    }
}

/** Builds [RecordingUi] from the controller's snapshot. */
internal object RecordingMapping {
    fun toUi(snapshot: RecordingUiSnapshot, paceMode: PaceMode): RecordingUi {
        val sport = snapshot.sport ?: SportType.RUN
        val last = snapshot.track.lastOrNull()
        return RecordingUi(
            status = snapshot.status,
            sport = sport,
            metrics = LiveMetrics.from(snapshot, paceMode),
            track = snapshot.track.map { LatLng(it.latitude, it.longitude) },
            location = last?.let {
                MapLocation(
                    position = LatLng(it.latitude, it.longitude),
                    accuracyMeters = snapshot.gpsStatus.accuracyMeters?.toFloat() ?: it.accuracyMeters?.toFloat(),
                    speedMetersPerSecond = it.speedMps.toFloat(),
                    timeMillis = it.epochMillis,
                )
            },
            gpsLost = sport.usesGps && snapshot.track.isNotEmpty() && !snapshot.gpsStatus.hasFix,
            recovered = snapshot.recoveredAfterInterruption,
            mockLocation = snapshot.isMockLocation,
        )
    }
}

/** The route as drawn on the record map. */
internal fun RouteDetail.toChoice(): RouteChoice = RouteChoice(
    id = route.id,
    name = route.name,
    distanceMeters = route.distanceMeters,
    points = points.map { LatLng(it.latitude, it.longitude) },
)
