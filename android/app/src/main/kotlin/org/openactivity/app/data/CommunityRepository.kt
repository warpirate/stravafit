// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.openactivity.app.model.Sport
import javax.inject.Inject
import javax.inject.Singleton

/** A club the viewer belongs to or might join. */
data class Club(
    val id: String,
    val name: String,
    val sport: Sport,
    val memberCount: Int,
    val city: String,
    val joined: Boolean,
    val hasNewPosts: Boolean = false,
)

/** A community challenge. [progress] is 0..1 for joined challenges and null otherwise. */
data class Challenge(
    val id: String,
    val title: String,
    val goal: String,
    val daysLeft: Int,
    val participants: Int,
    val progress: Float?,
)

/** Clubs and challenges for the Groups tab. */
interface CommunityRepository {
    val clubs: Flow<List<Club>>
    val challenges: Flow<List<Challenge>>
}

/** Static clubs and challenges until the Groups feature (blueprint phase 5) lands. */
@Singleton
class SampleCommunityRepository @Inject constructor() : CommunityRepository {
    override val clubs: Flow<List<Club>> = flowOf(
        listOf(
            Club("c1", "Riverside Runners", Sport.Run, memberCount = 1_240, city = "Pune", joined = true, hasNewPosts = true),
            Club("c2", "Commute Crew", Sport.Ride, memberCount = 86, city = "Pune", joined = true),
            Club("c3", "Sahyadri Gravel", Sport.GravelRide, memberCount = 412, city = "Lonavala", joined = false),
            Club("c4", "Sunrise Trail Society", Sport.TrailRun, memberCount = 318, city = "Pune", joined = false),
        ),
    )

    override val challenges: Flow<List<Challenge>> = flowOf(
        listOf(
            Challenge("ch1", "November 100 km", "Run 100 km this month", daysLeft = 2, participants = 5_480, progress = 0.72f),
            Challenge("ch2", "Climb 2,000 m", "Ride 2,000 m of elevation", daysLeft = 12, participants = 1_930, progress = null),
        ),
    )
}
