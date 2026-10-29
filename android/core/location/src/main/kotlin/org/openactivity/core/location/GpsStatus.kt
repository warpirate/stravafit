// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

/** Coarse GPS state for the record screen and the notification. */
enum class GpsState {
    /** Location permission missing. */
    NO_PERMISSION,

    /** Only approximate location granted. */
    APPROXIMATE_ONLY,

    /** Location switched off in system settings. */
    DISABLED,

    /** Waiting for a (fresh) fix. */
    SEARCHING,

    /** Fixes arrive but accuracy is worse than [GpsStatus.GOOD_ACCURACY_METERS]. */
    WEAK,

    /** Accuracy is [GpsStatus.GOOD_ACCURACY_METERS] or better ("GPS ready"). */
    GOOD,
}

/**
 * GPS status with the accuracy behind it, so the UI can size the accuracy halo and label it in text
 * (record detail 2.4, platform-extras R3).
 *
 * @property accuracyMeters horizontal accuracy of the latest fix, for [GpsState.WEAK] and [GpsState.GOOD].
 */
data class GpsStatus(val state: GpsState, val accuracyMeters: Double? = null) {
    /** True when a recording can expect usable fixes. */
    val hasFix: Boolean get() = state == GpsState.WEAK || state == GpsState.GOOD

    companion object {
        /** Accuracy at or under which the GPS counts as ready. */
        const val GOOD_ACCURACY_METERS: Double = 20.0

        val SEARCHING: GpsStatus = GpsStatus(GpsState.SEARCHING)

        /** Status for a fix with [accuracyMeters] (unknown accuracy counts as weak). */
        fun forAccuracy(accuracyMeters: Double?): GpsStatus = when {
            accuracyMeters != null && accuracyMeters <= GOOD_ACCURACY_METERS -> GpsStatus(GpsState.GOOD, accuracyMeters)
            else -> GpsStatus(GpsState.WEAK, accuracyMeters)
        }

        /** Status when fixes cannot be delivered. */
        fun unavailable(availability: LocationAvailability): GpsStatus = when (availability) {
            LocationAvailability.NO_PERMISSION -> GpsStatus(GpsState.NO_PERMISSION)
            LocationAvailability.APPROXIMATE_ONLY -> GpsStatus(GpsState.APPROXIMATE_ONLY)
            LocationAvailability.DISABLED -> GpsStatus(GpsState.DISABLED)
            LocationAvailability.AVAILABLE -> SEARCHING
        }
    }
}
