// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.openactivity.app.data.sample.SampleActivities
import org.openactivity.app.data.sample.SampleAthletes
import org.openactivity.app.model.Activity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Source of feed activities. Chronological, newest first (ADR-013). */
interface FeedRepository {
    /** All activities visible to the viewer, newest first. */
    val activities: Flow<List<Activity>>

    /** Gives or removes the viewer's high-five on [activityId]. Own activities are ignored. */
    suspend fun toggleHighFive(activityId: String)
}

/**
 * In-memory feed seeded with sample data until `:core:data` syncs with the backend.
 * High-fives update optimistically in memory.
 */
@Singleton
class SampleFeedRepository @Inject constructor(clock: Clock) : FeedRepository {

    private val state = MutableStateFlow(SampleActivities.feed(clock.instant()).sortedByDescending { it.startTime })

    override val activities: Flow<List<Activity>> = state.asStateFlow()

    override suspend fun toggleHighFive(activityId: String) {
        val me = SampleAthletes.me.name
        state.update { list ->
            list.map { activity ->
                when {
                    activity.id != activityId || activity.isOwn -> activity
                    activity.highFiveGiven -> activity.copy(highFiveGiven = false, highFivers = activity.highFivers - me)
                    else -> activity.copy(highFiveGiven = true, highFivers = listOf(me) + activity.highFivers)
                }
            }
        }
    }
}
