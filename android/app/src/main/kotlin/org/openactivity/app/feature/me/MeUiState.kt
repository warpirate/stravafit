// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import org.openactivity.app.model.LatLng
import org.openactivity.app.model.Sport
import org.openactivity.core.designsystem.component.Stat

/** Sub-tabs of the Me tab (IA 1.3: Progress · Activities). */
enum class MeTab(val label: String) {
    Progress("Progress"),
    Activities("Activities"),
}

/** Profile header. */
data class ProfileHeaderUi(
    val name: String,
    val location: String,
    val followers: String,
    val following: String,
)

/**
 * Twelve weekly bars, oldest first. [fractions] are 0..1 relative to the busiest week.
 */
data class WeeklyChartUi(
    val fractions: List<Float>,
    val maxLabel: String,
    val summary: String,
)

/** One row of the activities list. */
data class ActivityRowUi(
    val id: String,
    val title: String,
    val sport: Sport,
    val date: String,
    val statsLine: String,
    val visibilityLabel: String?,
    val route: List<LatLng>?,
)

/** State of the Me tab. */
data class MeUiState(
    val isLoading: Boolean = true,
    val header: ProfileHeaderUi? = null,
    val tab: MeTab = MeTab.Progress,
    val sports: List<Sport> = emptyList(),
    val selectedSport: Sport? = null,
    val thisWeek: List<Stat> = emptyList(),
    val weeklyChart: WeeklyChartUi? = null,
    val yearToDate: List<Stat> = emptyList(),
    val allTime: List<Stat> = emptyList(),
    val activities: List<ActivityRowUi> = emptyList(),
)
