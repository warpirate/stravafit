// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.geo.BoundingBox
import org.openactivity.model.LatLng

/** Whether the camera follows the user's location puck. */
enum class CameraTracking {
    /** The user moves the camera; location updates only move the puck. */
    Free,

    /** The camera stays centred on the puck until the user pans. */
    Follow,
}

/** A one-shot camera instruction, executed by the map once it is ready and laid out. */
sealed interface CameraCommand {
    val animate: Boolean

    /** Show all of [bounds] with [padding] clear. */
    data class FitBounds(
        val bounds: BoundingBox,
        val padding: MapPadding,
        val maxZoom: Double,
        override val animate: Boolean,
    ) : CameraCommand

    /** Move to [camera]. */
    data class MoveTo(val camera: MapCamera, override val animate: Boolean) : CameraCommand
}

/**
 * Hoisted state for [RidgelineMap]: the camera the map last settled on and whether it follows the
 * user. Create it with [rememberMapState] so it survives configuration changes and process death.
 *
 * Calls such as [fitTo] are queued and run once the map has loaded and measured itself, so they can
 * be issued straight from a `LaunchedEffect` on first composition.
 */
@Stable
class MapState(initialCamera: MapCamera? = null, initialTracking: CameraTracking = CameraTracking.Free) {
    /** Where the camera came to rest last; null until the map has settled once. */
    var camera: MapCamera? by mutableStateOf(initialCamera)
        internal set

    /** Whether the camera follows the puck. Panning the map switches this back to [CameraTracking.Free]. */
    var tracking: CameraTracking by mutableStateOf(initialTracking)

    /** True once the base map style has loaded. */
    var isStyleLoaded: Boolean by mutableStateOf(false)
        internal set

    private val pending = Channel<CameraCommand>(capacity = PENDING_COMMANDS, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    internal val commands: Flow<CameraCommand> get() = pending.receiveAsFlow()

    /** Starts following the user's location (the recentre button). */
    fun followUser() {
        tracking = CameraTracking.Follow
    }

    /** Stops following without moving the camera. */
    fun stopFollowing() {
        tracking = CameraTracking.Free
    }

    /**
     * Fits the camera to [points] (no-op for an empty list), leaving [padding] dp clear. Stops
     * following the user, since the two would fight.
     */
    fun fitTo(
        points: Collection<LatLng>,
        padding: MapPadding = MapPadding.all(DEFAULT_FIT_PADDING_DP),
        maxZoom: Double = DEFAULT_FIT_MAX_ZOOM,
        animate: Boolean = true,
    ) {
        val bounds = BoundingBox.of(points) ?: return
        fitTo(bounds, padding, maxZoom, animate)
    }

    /** Fits the camera to [bounds]; see [fitTo]. */
    fun fitTo(
        bounds: BoundingBox,
        padding: MapPadding = MapPadding.all(DEFAULT_FIT_PADDING_DP),
        maxZoom: Double = DEFAULT_FIT_MAX_ZOOM,
        animate: Boolean = true,
    ) {
        tracking = CameraTracking.Free
        pending.trySend(CameraCommand.FitBounds(bounds, padding, maxZoom, animate))
    }

    /** Moves the camera to [camera], stopping follow mode. */
    fun moveTo(camera: MapCamera, animate: Boolean = true) {
        tracking = CameraTracking.Free
        pending.trySend(CameraCommand.MoveTo(camera, animate))
    }

    companion object {
        /** Default inset when fitting content, in dp. */
        const val DEFAULT_FIT_PADDING_DP: Float = 48f

        /** Fitting never zooms in further than this, so a short track still shows its surroundings. */
        const val DEFAULT_FIT_MAX_ZOOM: Double = 17.0

        private const val PENDING_COMMANDS = 8

        /** Saves the camera and tracking mode as primitives. */
        val Saver: Saver<MapState, Any> = Saver(
            save = { state ->
                val c = state.camera
                listOf(
                    state.tracking.name,
                    c?.target?.latitude, c?.target?.longitude, c?.zoom, c?.bearing, c?.tilt,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val list = saved as List<Any?>
                val lat = list[1] as Double?
                val lon = list[2] as Double?
                val camera = if (lat != null && lon != null) {
                    MapCamera(LatLng(lat, lon), list[3] as Double, list[4] as Double, list[5] as Double)
                } else {
                    null
                }
                MapState(camera, CameraTracking.valueOf(list[0] as String))
            },
        )
    }
}

/**
 * Remembers a [MapState] across recomposition, configuration changes and process death.
 *
 * @param initialCamera camera before the map has been moved, e.g. a last known region.
 * @param initialTracking start in follow mode for screens centred on the user, such as Record.
 */
@Composable
fun rememberMapState(
    initialCamera: MapCamera? = null,
    initialTracking: CameraTracking = CameraTracking.Free,
): MapState = rememberSaveable(saver = MapState.Saver) { MapState(initialCamera, initialTracking) }
