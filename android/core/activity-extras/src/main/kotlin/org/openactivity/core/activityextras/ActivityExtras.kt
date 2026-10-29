// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import java.io.File

/**
 * Extras of one activity that live outside the activity database.
 *
 * @property muted true when the activity is kept out of home and club feeds. It still shows on the
 *   owner's profile and still counts towards totals, goals and challenges.
 * @property photos attached photos, oldest first, at most [MAX_PHOTOS].
 */
public data class ActivityExtras(
    val muted: Boolean = false,
    val photos: List<ActivityPhoto> = emptyList(),
) {
    public companion object {
        /** Nothing set. */
        public val NONE: ActivityExtras = ActivityExtras()

        /** Most photos one activity can hold (save-edit-activity M4: one documented limit everywhere). */
        public const val MAX_PHOTOS: Int = 12
    }
}

/**
 * A photo stored in the app's private files.
 *
 * Photos are re-encoded as JPEG when they are added, which drops EXIF metadata such as the GPS
 * position where the picture was taken; the visible orientation is baked into the pixels first.
 *
 * @property id stable identifier, unique within the activity.
 * @property file the JPEG on disk.
 * @property width pixel width after re-encoding.
 * @property height pixel height after re-encoding.
 */
public data class ActivityPhoto(
    val id: String,
    val file: File,
    val width: Int,
    val height: Int,
)

/** Result of adding photos: how many were stored and how many were skipped (limit or unreadable). */
public data class AddPhotosResult(val added: Int, val skippedOverLimit: Int, val failed: Int)
