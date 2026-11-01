// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import org.openactivity.app.data.local.LocalPreferences
import javax.inject.Inject

/**
 * Which feed items the viewer has already seen (home-feed 1.3): everything up to a database id.
 * Ids only grow, so an activity imported today but recorded last year still counts as unseen.
 */
interface FeedSeenRepository {
    /** The highest seen id, or null before the feed was ever shown. */
    val seenThroughId: Flow<Long?>

    /** Marks every item up to [id] as seen. Never moves back. */
    suspend fun markSeenThrough(id: Long)
}

/** [FeedSeenRepository] in the app's local DataStore. */
class LocalFeedSeenRepository @Inject constructor(private val local: LocalPreferences) : FeedSeenRepository {
    override val seenThroughId: Flow<Long?> = local.feedSeenThroughId

    override suspend fun markSeenThrough(id: Long) = local.markFeedSeenThrough(id)
}
