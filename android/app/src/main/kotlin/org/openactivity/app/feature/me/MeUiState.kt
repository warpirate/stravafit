// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.LatLng
import org.openactivity.model.SportType

/** Sub-tabs of the Me tab (IA 1.3: Progress · Activities). */
enum class MeTab(val label: String) {
    Progress("Progress"),
    Activities("Activities"),
}

/**
 * Profile header.
 *
 * @property summary all-time line, e.g. "12 activities · 84.2 km".
 */
data class ProfileHeaderUi(
    val name: String,
    val location: String,
    val photoPath: String?,
    val summary: String,
)

/**
 * Twelve weekly bars, oldest first. [fractions] are 0..1 relative to the busiest week.
 *
 * @property metric what the bars measure ("Distance" or "Time").
 */
data class WeeklyChartUi(
    val fractions: List<Float>,
    val maxLabel: String,
    val summary: String,
    val metric: String,
    /** This week's value with its unit ("32.4 km"), for the callout on the last point. */
    val currentLabel: String = "",
    /** Average per week over the chart ("28.7 km"). */
    val averageLabel: String = "",
)

/** One row of the activities list. */
data class ActivityRowUi(
    val id: Long,
    val title: String,
    val sport: SportType,
    val date: String,
    val statsLine: String,
    val visibilityLabel: String?,
    val route: List<LatLng>?,
)

/** One line of the Best Efforts table: the all-time best for a target distance. */
data class BestEffortUi(
    val label: String,
    val time: String,
    val date: String,
    val activityId: Long,
)

/**
 * State of the Me tab.
 *
 * @property sports sports with at least one activity, for the progress chips; [selectedSport] null = all.
 * @property bestEffortsTitle "Running best efforts" or "Cycling best efforts", null when the sport has none.
 */
data class MeUiState(
    val isLoading: Boolean = true,
    val header: ProfileHeaderUi? = null,
    val tab: MeTab = MeTab.Progress,
    val hasAnyActivity: Boolean = false,
    val sports: List<SportType> = emptyList(),
    val selectedSport: SportType? = null,
    val thisWeek: List<Stat> = emptyList(),
    val weeklyChart: WeeklyChartUi? = null,
    val year: Int = 0,
    val yearToDate: List<Stat> = emptyList(),
    val allTime: List<Stat> = emptyList(),
    val bestEffortsTitle: String? = null,
    val bestEfforts: List<BestEffortUi> = emptyList(),
    val query: String = "",
    val activitySport: SportType? = null,
    val activities: List<ActivityRowUi> = emptyList(),
    /** "26 Oct – 1 Nov": the current week. */
    val weekRange: String = "",
    /** Training log: one entry per day Monday to Sunday, 0..1 relative to the busiest day (0 = rest). */
    val weekDays: List<Float> = emptyList(),
    /** Index of today in [weekDays]. */
    val todayIndex: Int = -1,
)
