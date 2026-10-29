// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Photos and the mute flag of saved activities, keyed by the activity's database id.
 *
 * Everything is stored on the device only. Implementations are safe to call from any thread.
 */
public interface ActivityExtrasRepository {
    /** Extras of [activityId]; emits [ActivityExtras.NONE] when nothing was ever set. */
    public fun observe(activityId: Long): Flow<ActivityExtras>

    /** Current extras of [activityId]. */
    public suspend fun get(activityId: Long): ActivityExtras

    /** Sets whether [activityId] is muted from feeds. */
    public suspend fun setMuted(activityId: Long, muted: Boolean)

    /**
     * Copies the pictures at [uris] (content or file URIs, usually from the system photo picker) into
     * private storage, re-encoded without location metadata. Stops at [ActivityExtras.MAX_PHOTOS].
     */
    public suspend fun addPhotos(activityId: Long, uris: List<Uri>): AddPhotosResult

    /** Removes the photo [photoId] from [activityId] and deletes its file. */
    public suspend fun removePhoto(activityId: Long, photoId: String)

    /** Deletes every extra of [activityId], including photo files. Call it when the activity is deleted. */
    public suspend fun deleteAll(activityId: Long)
}
