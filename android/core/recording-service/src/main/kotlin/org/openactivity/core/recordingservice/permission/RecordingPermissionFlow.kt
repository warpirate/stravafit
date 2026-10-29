// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.permission

/** What the permission helper shows or does next. */
enum class PermissionStep {
    /** Nothing on screen. */
    IDLE,

    /** Explain why precise location is needed, then ask. */
    LOCATION_RATIONALE,

    /** Location was denied for good: explain and offer the app settings. */
    LOCATION_SETTINGS,

    /** Explain the recording notification, then ask (Android 13+). */
    NOTIFICATION_RATIONALE,
}

/** A snapshot of the permissions recording uses. */
data class PermissionSnapshot(
    val preciseLocation: Boolean,
    val notifications: Boolean,
    /** True when the system still shows its dialog for location (not "don't ask again"). */
    val canAskLocation: Boolean,
    /** Notification permission exists on this Android version and has not been asked yet in this flow. */
    val shouldAskNotifications: Boolean,
)

/**
 * The in-context permission flow for the first Start (record detail 1.3, recording design §5.3):
 * precise location is required, the notification permission is asked once but never blocks recording,
 * and background location is not needed because the foreground service starts while the app is visible.
 * Pure, so it is unit-tested apart from the Compose UI that drives it.
 */
object RecordingPermissionFlow {
    /** Where to go when the athlete taps Start. Returns null when recording can start now. */
    fun onStart(p: PermissionSnapshot): PermissionStep? = when {
        !p.preciseLocation && p.canAskLocation -> PermissionStep.LOCATION_RATIONALE
        !p.preciseLocation -> PermissionStep.LOCATION_SETTINGS
        p.shouldAskNotifications && !p.notifications -> PermissionStep.NOTIFICATION_RATIONALE
        else -> null
    }

    /**
     * After the system location dialog. A denial that leaves the system unwilling to ask again means the
     * athlete chose "don't ask again" (or denied twice): send them to settings.
     */
    fun afterLocationResult(p: PermissionSnapshot): PermissionStep? = when {
        p.preciseLocation -> onStart(p)
        !p.canAskLocation -> PermissionStep.LOCATION_SETTINGS
        else -> PermissionStep.IDLE
    }
}
