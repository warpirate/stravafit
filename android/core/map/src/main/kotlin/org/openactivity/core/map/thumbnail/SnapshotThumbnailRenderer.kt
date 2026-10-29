// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.snapshotter.MapSnapshotter
import org.openactivity.core.map.render.toCameraPosition
import org.openactivity.core.map.render.toStyleBuilder
import org.openactivity.core.map.style.MapStyleProvider
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Renders a real base-map image with MapLibre's [MapSnapshotter] (off-screen, no MapView), then
 * draws the route on top with [RoutePainter] using the same camera, so it matches the live map.
 *
 * Needs the network for tiles; returns null on failure so the caller can fall back.
 */
class SnapshotThumbnailRenderer(
    private val context: Context,
    private val styles: MapStyleProvider,
) : RouteThumbnailRenderer {
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun render(request: ThumbnailRequest): Bitmap? {
        val camera = request.camera ?: return null
        // MapSnapshotter must be created and driven on the main thread.
        val snapshot = withContext(Dispatchers.Main) {
            MapLibre.getInstance(context.applicationContext)
            val options = MapSnapshotter.Options(request.widthDp.roundToInt().coerceAtLeast(1), request.heightDp.roundToInt().coerceAtLeast(1))
                .withStyleBuilder(styles.style(request.style).toStyleBuilder())
                .withCameraPosition(camera.toCameraPosition())
                .withPixelRatio(request.density)
                .withLogo(false)
            val snapshotter = MapSnapshotter(context.applicationContext, options)
            try {
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    // The handler may run on the cancelling thread; the snapshotter is main-thread only.
                    cont.invokeOnCancellation { mainHandler.post { snapshotter.cancel() } }
                    snapshotter.start(
                        { result -> if (cont.isActive) cont.resume(result.bitmap) },
                        { error ->
                            Log.w(TAG, "Snapshot failed: $error")
                            if (cont.isActive) cont.resume(null)
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(TAG, "Snapshot failed", e)
                null
            }
        } ?: return null

        return withContext(Dispatchers.Default) {
            // The snapshot is rendered at whole dp; scale to the exact requested pixels if needed.
            val sized = if (snapshot.width == request.widthPx && snapshot.height == request.heightPx && snapshot.isMutable) {
                snapshot
            } else {
                Bitmap.createScaledBitmap(snapshot, request.widthPx, request.heightPx, true)
                    .let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
            }
            RoutePainter.draw(
                canvas = Canvas(sized),
                points = request.points,
                camera = camera,
                density = request.density,
                lineColor = request.palette.activityRoute,
                casingColor = request.palette.casing,
                markerColor = request.palette.marker,
            )
            sized
        }
    }

    private companion object {
        const val TAG = "RouteSnapshot"
    }
}
