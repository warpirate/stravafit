// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.feature.feed.FeedViewModel
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.activityStats
import org.openactivity.app.ui.common.decodeRoute
import org.openactivity.app.ui.common.statsLine
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.model.SportType
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Me tab: local profile header, progress (12 weekly bars, this week / this year / all time per
 * sport, best efforts) and the searchable list of your activities. Everything is computed from the
 * activities stored on this device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    preferencesRepository: PreferencesRepository,
    private val timeFormatter: RelativeTimeFormatter,
    private val clock: Clock,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())
    private val progressSport = selection.map { it.sport }.distinctUntilChanged()

    /**
     * The current year, read again whenever collection restarts (the screen comes back after the
     * sharing timeout), so the year totals roll over without restarting the app.
     */
    private val currentYear: Flow<Int> = flow { emit(LocalDate.now(clock).year) }

    private val progress: Flow<Progress> = combine(
        progressSport.flatMapLatest { sport -> activityRepository.observeWeeklyTotals(WEEKS, sport?.let { setOf(it) }) },
        currentYear.flatMapLatest(activityRepository::observeYearTotals),
        activityRepository.observeAllTimeTotals(),
        progressSport.flatMapLatest(::bestEfforts),
    ) { weekly, yearTotals, allTime, bests -> Progress(weekly, yearTotals, allTime, bests) }

    val uiState: StateFlow<MeUiState> = combine(
        activityRepository.observeActivities(),
        profileRepository.profile,
        preferencesRepository.preferences,
        selection,
        progress,
    ) { activities, profile, preferences, selected, progress ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        val sports = progress.allTime.keys.sortedByDescending { progress.allTime[it]?.count ?: 0 }
        val sport = selected.sport?.takeIf { it in sports }
        val timeOnly = sport != null && !sport.usesGps
        val allTimeTotals = if (sport == null) progress.allTime.values.fold(StatTotals.ZERO, StatTotals::plus) else progress.allTime[sport]
        val yearTotals = if (sport == null) progress.year.totals else progress.year.bySport[sport]
        val everything = progress.allTime.values.fold(StatTotals.ZERO, StatTotals::plus)
        MeUiState(
            isLoading = false,
            header = ProfileHeaderUi(
                name = profile.displayName,
                location = profile.location,
                photoPath = profile.photoPath,
                summary = headerSummary(everything, formatter),
            ),
            tab = selected.tab,
            hasAnyActivity = activities.isNotEmpty(),
            sports = sports,
            selectedSport = sport,
            thisWeek = ProgressFormat.totalsStats(progress.weekly.lastOrNull()?.totals ?: StatTotals.ZERO, formatter, includeCount = true, timeOnly),
            weeklyChart = progress.weekly.takeIf { it.isNotEmpty() }?.let { ProgressFormat.weeklyChart(it, formatter, timeOnly) },
            year = progress.year.year,
            yearToDate = ProgressFormat.totalsStats(yearTotals ?: StatTotals.ZERO, formatter, includeCount = true, timeOnly),
            allTime = ProgressFormat.totalsStats(allTimeTotals ?: StatTotals.ZERO, formatter, includeCount = true, timeOnly),
            bestEffortsTitle = bestEffortsTitle(sport, progress.bests),
            bestEfforts = progress.bests.map { best ->
                BestEffortUi(
                    label = ProgressFormat.effortLabel(best.target, formatter),
                    time = ProgressFormat.effortTime(best.value),
                    date = timeFormatter.shortDate(best.achievedAt),
                    activityId = best.activityId,
                )
            },
            query = selected.query,
            activitySport = selected.activitySport,
            activities = activities
                .filter { selected.activitySport == null || it.sport == selected.activitySport }
                .filter { matches(it, selected.query) }
                .sortedByDescending { it.startTime }
                .map { it.toRow(formatter) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MeUiState())

    fun selectTab(tab: MeTab) = selection.update { it.copy(tab = tab) }

    /** Progress for one sport, or all sports with null. */
    fun selectSport(sport: SportType?) = selection.update { it.copy(sport = sport) }

    /** Filters the activities list by title. */
    fun search(query: String) = selection.update { it.copy(query = query) }

    /** Filters the activities list by sport, or shows all with null. */
    fun filterActivities(sport: SportType?) = selection.update { it.copy(activitySport = sport) }

    private fun bestEfforts(sport: SportType?): Flow<List<PersonalBest>> = when {
        sport == null || sport in ProgressFormat.RUN_SPORTS -> activityRepository.observePersonalBests(ProgressFormat.RUN_SPORTS)
        sport in ProgressFormat.RIDE_SPORTS -> activityRepository.observePersonalBests(ProgressFormat.RIDE_SPORTS)
        else -> flowOf(emptyList())
    }

    private fun bestEffortsTitle(sport: SportType?, bests: List<PersonalBest>): String? = when {
        bests.isEmpty() -> null
        sport == null || sport in ProgressFormat.RUN_SPORTS -> "Running best efforts"
        else -> "Cycling best efforts"
    }

    private fun SavedActivity.toRow(formatter: UnitFormatter) = ActivityRowUi(
        id = id,
        title = title,
        sport = sport,
        date = timeFormatter.shortDate(startTime),
        statsLine = activityStats(this, formatter).statsLine(),
        visibilityLabel = FeedViewModel.visibilityLabel(visibility),
        route = decodeRoute(polyline),
    )

    private data class Selection(
        val tab: MeTab = MeTab.Progress,
        val sport: SportType? = null,
        val query: String = "",
        val activitySport: SportType? = null,
    )

    private data class Progress(
        val weekly: List<WeekTotals>,
        val year: YearTotals,
        val allTime: Map<SportType, StatTotals>,
        val bests: List<PersonalBest>,
    )

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Weeks in the progress chart (profile-progress C2, "3M" = 12 weeks). */
        const val WEEKS = 12

        /** Case-insensitive title or description match; a blank query matches everything. */
        fun matches(activity: SavedActivity, query: String): Boolean {
            val q = query.trim()
            if (q.isEmpty()) return true
            return activity.title.contains(q, ignoreCase = true) || activity.description.contains(q, ignoreCase = true)
        }

        /** "12 activities · 84.2 km all time", or a nudge before the first one. */
        fun headerSummary(totals: StatTotals, formatter: UnitFormatter): String {
            if (totals.count == 0) return "No activities yet"
            val noun = if (totals.count == 1) "activity" else "activities"
            val distance = formatter.distance(totals.distanceMeters)
            return if (totals.distanceMeters > 0) {
                "${formatter.count(totals.count)} $noun · ${distance.value} ${distance.unit} all time"
            } else {
                "${formatter.count(totals.count)} $noun all time"
            }
        }
    }
}
