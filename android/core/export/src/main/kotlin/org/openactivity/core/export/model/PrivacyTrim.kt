// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.model

import org.openactivity.geo.PrivacyTrimOptions
import org.openactivity.geo.PrivacyTrimmer
import org.openactivity.geo.PrivacyZone
import org.openactivity.model.LatLng

/**
 * Result of applying [ExportActivity.privacy] to an activity.
 *
 * @property activity the activity with every sample outside the visible part stripped of its position.
 *   Time, heart rate and other sensor values are kept, so data files stay useful without revealing
 *   where the owner started or finished.
 * @property routeHidden true when so little would remain that no route may be shown at all.
 * @property hiddenPoints number of samples whose position was removed.
 */
public data class TrimmedActivity(
    val activity: ExportActivity,
    val routeHidden: Boolean,
    val hiddenPoints: Int,
)

/**
 * Applies the owner's privacy settings using the shared [PrivacyTrimmer], so a shared image or file
 * hides exactly what the app hides everywhere else. Trimming happens before any projection or
 * simplification, which keeps the hidden part from influencing the framing.
 */
public fun ExportActivity.privacyTrimmed(): TrimmedActivity {
    val positioned = points.withIndex().filter { it.value.hasPosition }
    if (positioned.isEmpty()) return TrimmedActivity(this, routeHidden = false, hiddenPoints = 0)

    val options = PrivacyTrimOptions(
        hideStartMeters = privacy.hideStartMeters,
        hideEndMeters = privacy.hideEndMeters,
        zones = privacy.zones.map { PrivacyZone(LatLng(it.latitude, it.longitude), it.radiusMeters) },
    )
    val result = PrivacyTrimmer.trim(positioned.map { LatLng(it.value.latitude!!, it.value.longitude!!) }, options)
    val visibleRange = if (result.hideEntireMap) {
        IntRange.EMPTY
    } else {
        positioned[result.firstVisibleIndex].index..positioned[result.lastVisibleIndex].index
    }
    var hidden = 0
    val trimmedPoints = points.mapIndexed { i, p ->
        if (!p.hasPosition || i in visibleRange) p else p.copy(latitude = null, longitude = null).also { hidden++ }
    }
    return TrimmedActivity(copy(points = trimmedPoints), routeHidden = result.hideEntireMap, hiddenPoints = hidden)
}
