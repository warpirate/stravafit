// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import org.openactivity.app.model.LatLng
import org.openactivity.app.model.Sport
import org.openactivity.core.designsystem.component.Stat

/** Which feed the header switch shows (home-feed checklist 1.1). Both are chronological. */
enum class FeedScope(val label: String) {
    Following("Following"),
    You("You"),
}

/** Display model for one activity card, already formatted for the viewer. */
data class ActivityCardUi(
    val id: String,
    val athleteName: String,
    /** "Today at 7:02 AM · Pune" */
    val meta: String,
    /** "Only you" / "Followers" for the owner's non-public activities, else null. */
    val visibilityLabel: String?,
    /** "with Sam, Li +1 other" for group activities, else null. */
    val withLine: String?,
    val sport: Sport,
    val title: String,
    val description: String?,
    val stats: List<Stat>,
    /** Null for activities without GPS, which show a sport tile instead of a map. */
    val route: List<LatLng>?,
    /** "2 achievements · 2nd best 5K", or null. */
    val achievementText: String?,
    val highFiverNames: List<String>,
    val highFiveCount: Int,
    val highFiveGiven: Boolean,
    val commentCount: Int,
    val isOwn: Boolean,
)

/** State of the Feed screen. */
data class FeedUiState(
    val scope: FeedScope = FeedScope.Following,
    val isLoading: Boolean = true,
    val cards: List<ActivityCardUi> = emptyList(),
    val unreadNotifications: Int = 0,
) {
    /** True once loaded with nothing to show. */
    val isEmpty: Boolean get() = !isLoading && cards.isEmpty()
}
