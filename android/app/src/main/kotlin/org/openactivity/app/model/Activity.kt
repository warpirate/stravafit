// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

import java.time.Instant

/** A geographic coordinate in WGS84 degrees. */
data class LatLng(val latitude: Double, val longitude: Double)

/** Public identity of a person in the app. */
data class Athlete(
    val id: String,
    val name: String,
    val location: String,
)

/** Who can see an activity. Shown on the owner's card when it is not [Everyone]. */
enum class Visibility(val label: String) {
    Everyone("Everyone"),
    Followers("Followers"),
    OnlyMe("Only you"),
}

/**
 * A finished activity as shown in the feed and activity lists.
 *
 * Optional numbers are null when unknown or hidden by the owner, and render as a dash, never "0".
 *
 * @property route simplified track for the thumbnail; null for indoor activities or hidden maps.
 * @property withAthletes names of people matched as a group activity.
 */
data class Activity(
    val id: String,
    val athlete: Athlete,
    val sport: Sport,
    val title: String,
    val description: String? = null,
    val startTime: Instant,
    val distanceMeters: Double?,
    val movingTimeSeconds: Long,
    val elevationGainMeters: Double? = null,
    val averageHeartRate: Int? = null,
    val calories: Int? = null,
    val route: List<LatLng>? = null,
    val achievementCount: Int = 0,
    val topAchievement: String? = null,
    val highFivers: List<String> = emptyList(),
    val highFiveGiven: Boolean = false,
    val commentCount: Int = 0,
    val isOwn: Boolean = false,
    val visibility: Visibility = Visibility.Everyone,
    val withAthletes: List<String> = emptyList(),
) {
    /** Number of high-fives, including the viewer's own. */
    val highFiveCount: Int get() = highFivers.size
}
