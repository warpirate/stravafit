// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.LatLng
import org.openactivity.model.SportType

/**
 * Which feed the header switch shows (home-feed checklist 1.1). Both are chronological.
 * Following is your activities plus those of the athletes you follow (following needs a server account,
 * so for now it shows your own); You is only your own.
 */
enum class FeedScope(val label: String) {
    Following("Following"),
    You("You"),
}

/** Display model for one activity card, already formatted for the viewer. */
data class ActivityCardUi(
    val id: Long,
    val athleteName: String,
    val athletePhotoPath: String?,
    /** "Today at 07:02 · Pune" */
    val meta: String,
    /** "Only you" / "Followers" for non-public activities, else null. */
    val visibilityLabel: String?,
    val sport: SportType,
    val title: String,
    val description: String?,
    val stats: List<Stat>,
    /** Null for activities without GPS, which show a sport tile instead of a map. */
    val route: List<LatLng>?,
    /** High-fives received. Stays 0 until the activity is shared to a server. */
    val highFiveCount: Int,
    /** The trophy line, or null without personal records. */
    val achievements: Achievements? = null,
)

/** The trophy line of a card: [count] personal records, the most notable being [highlight] ("1K"). */
data class Achievements(val count: Int, val highlight: String)

/**
 * State of the Feed screen.
 *
 * @property sports sports that occur in your activities, for the filter chips (home-feed 1.9).
 * @property sportFilter selected sport chip, or null for all.
 * @property message one-shot snackbar text, cleared with [FeedViewModel.messageShown].
 */
data class FeedUiState(
    val scope: FeedScope = FeedScope.Following,
    val isLoading: Boolean = true,
    val cards: List<ActivityCardUi> = emptyList(),
    val sports: List<SportType> = emptyList(),
    val sportFilter: SportType? = null,
    val hasAnyActivity: Boolean = false,
    val message: String? = null,
    /** A recording is in progress: the empty state points to it rather than to a new one. */
    val recordingInProgress: Boolean = false,
    /** The bell shows an unread dot until it has been opened once. */
    val hasUnreadNotifications: Boolean = true,
) {
    /** True once loaded with nothing to show in the selected scope. */
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()
}
