// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import org.openactivity.model.LatLng
import org.openactivity.polyline.PolylineCodec

/**
 * Decodes a stored overview polyline (precision 5) into points for thumbnails and maps.
 * Returns null for a missing, corrupt or single-point polyline, so callers fall back to a sport tile.
 */
fun decodeRoute(polyline: String?): List<LatLng>? {
    if (polyline.isNullOrEmpty()) return null
    val points = try {
        PolylineCodec.decode(polyline)
    } catch (e: IllegalArgumentException) {
        return null
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
    return points.takeIf { it.size >= 2 }
}
