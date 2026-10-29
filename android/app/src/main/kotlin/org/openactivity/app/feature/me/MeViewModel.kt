// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.FeedRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.data.SportTotals
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.Activity
import org.openactivity.app.model.Sport
import org.openactivity.app.model.Visibility
import org.openactivity.app.ui.common.activityStats
import org.openactivity.core.designsystem.component.Stat
import java.util.Locale
import javax.inject.Inject

/** Me tab: profile header, weekly progress per sport, totals and the own-activities list. */
@HiltViewModel
class MeViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    feedRepository: FeedRepository,
    preferencesRepository: PreferencesRepository,
    private val timeFormatter: RelativeTimeFormatter,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<MeUiState> = combine(
        profileRepository.profile,
        feedRepository.activities,
        preferencesRepository.preferences,
        selection,
    ) { profile, activities, preferences, selected ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        val sports = profile.progress.map { it.sport }
        val sport = selected.sport?.takeIf { it in sports } ?: sports.firstOrNull()
        val progress = profile.progress.firstOrNull { it.sport == sport }
        MeUiState(
            isLoading = false,
            header = ProfileHeaderUi(
                name = profile.athlete.name,
                location = profile.athlete.location,
                followers = formatter.count(profile.followers),
                following = formatter.count(profile.following),
            ),
            tab = selected.tab,
            sports = sports,
            selectedSport = sport,
            thisWeek = progress?.thisWeek?.let { totalsStats(it, formatter, includeCount = false) }.orEmpty(),
            weeklyChart = progress?.let { weeklyChart(it.weeklyDistanceMeters, formatter) },
            yearToDate = progress?.yearToDate?.let { totalsStats(it, formatter, includeCount = true) }.orEmpty(),
            allTime = progress?.allTime?.let { totalsStats(it, formatter, includeCount = true) }.orEmpty(),
            activities = activities.filter { it.isOwn }.sortedByDescending { it.startTime }.map { it.toRow(formatter) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MeUiState())

    fun selectTab(tab: MeTab) {
        selection.value = selection.value.copy(tab = tab)
    }

    fun selectSport(sport: Sport) {
        selection.value = selection.value.copy(sport = sport)
    }

    private fun Activity.toRow(formatter: UnitFormatter): ActivityRowUi {
        val stats = activityStats(this, formatter)
        return ActivityRowUi(
            id = id,
            title = title,
            sport = sport,
            date = timeFormatter.shortDate(startTime),
            statsLine = stats.filter { it.value != null }.joinToString(" · ") { stat ->
                val unit = stat.unit?.let { if (it.startsWith("/")) it else " $it" }.orEmpty()
                "${stat.value}$unit"
            },
            visibilityLabel = visibility.takeIf { it != Visibility.Everyone }?.label,
            route = route?.takeIf { sport.usesGps },
        )
    }

    private data class Selection(val tab: MeTab = MeTab.Progress, val sport: Sport? = null)

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun totalsStats(totals: SportTotals, formatter: UnitFormatter, includeCount: Boolean): List<Stat> = buildList {
            if (includeCount) add(Stat("Activities", formatter.count(totals.count)))
            val distance = totals.distanceMeters?.let { formatter.distance(it) }
            add(Stat("Distance", distance?.value, distance?.unit, distance?.spokenUnit))
            add(Stat("Time", UnitFormatter.compactDuration(totals.movingTimeSeconds)))
            val elevation = totals.elevationMeters?.let { formatter.elevation(it) }
            add(Stat("Elev gain", elevation?.value, elevation?.unit, elevation?.spokenUnit))
        }

        fun weeklyChart(weeklyMeters: List<Double>, formatter: UnitFormatter): WeeklyChartUi {
            val max = weeklyMeters.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val maxMeasure = formatter.distance(max)
            val current = formatter.distance(weeklyMeters.lastOrNull() ?: 0.0)
            return WeeklyChartUi(
                fractions = weeklyMeters.map { (it / max).toFloat() },
                maxLabel = "${maxMeasure.value} ${maxMeasure.unit}",
                summary = "Last ${weeklyMeters.size} weeks. This week ${current.value} ${current.spokenUnit}, " +
                    "busiest week ${maxMeasure.value} ${maxMeasure.spokenUnit}.",
            )
        }
    }
}
