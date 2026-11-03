// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.core.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed data of the "This week" and "Last activity" widgets; offline and without an account. */
@Singleton
class SummaryRepository @Inject constructor(
    private val activities: ActivityRepository,
    private val goals: GoalRepository,
    private val settings: SettingsRepository,
) {
    /** This week's totals and weekly goal, in the app's units and week start. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun week(): Flow<WeekSummary> = settings.settings
        .map { it.units to it.firstDayOfWeek }
        .distinctUntilChanged()
        .flatMapLatest { (units, firstDay) ->
            combine(activities.observeWeeklyTotals(1, null, firstDay), goals.observeProgress()) { weeks, progress ->
                val week = weeks.last()
                weekSummary(week.weekStart, week.totals, progress, units)
            }
        }
        .distinctUntilChanged()

    /** The newest activity, or null when there is none. */
    fun last(): Flow<LastActivityCard?> = combine(
        activities.observeLatestActivity(),
        settings.settings.map { it.units }.distinctUntilChanged(),
    ) { latest, units -> lastActivityCard(latest, units) }
        .distinctUntilChanged()
}
