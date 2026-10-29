// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

/**
 * The athlete's local profile. Nothing here leaves the device until the user signs in to a server.
 *
 * @property name what the athlete wants to be called; blank until set.
 * @property location free-text city or region; blank when not shared.
 * @property photoPath absolute path of the cropped profile photo in app storage, or null.
 */
data class UserProfile(
    val name: String = "",
    val location: String = "",
    val photoPath: String? = null,
) {
    /** Name to display, "You" until one is set. */
    val displayName: String get() = name.trim().ifEmpty { DEFAULT_NAME }

    companion object {
        const val DEFAULT_NAME: String = "You"
    }
}
