// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.UserProfile
import org.openactivity.app.ui.common.activityStats
import org.openactivity.app.ui.common.decodeRoute
import org.openactivity.app.feature.me.ProgressFormat
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.model.SportType
import org.openactivity.model.Visibility
import java.util.Locale
import javax.inject.Inject

/**
 * Feed screen state. The You feed lists the activities stored on this device, newest first
 * (ADR-013), formatted in the viewer's units; the Following feed needs a server account and
 * stays empty until sign-in exists.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    preferencesRepository: PreferencesRepository,
    private val timeFormatter: RelativeTimeFormatter,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    /** Personal records by activity id, over runs and rides separately. */
    private val records = combine(
        activityRepository.observePersonalBests(ProgressFormat.RUN_SPORTS),
        activityRepository.observePersonalBests(ProgressFormat.RIDE_SPORTS),
    ) { runs, rides -> (runs + rides).distinctBy { it.activityId to it.target }.groupBy { it.activityId } }

    val uiState: StateFlow<FeedUiState> = combine(
        activityRepository.observeActivities(),
        profileRepository.profile,
        preferencesRepository.preferences,
        selection,
        records,
    ) { activities, profile, preferences, selected, prs ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        val sports = activities.map { it.sport }.distinct()
        val filter = selected.sport?.takeIf { it in sports }
        // Following is you plus the athletes you follow; without a server account that is only you.
        val cards = activities
            .filter { filter == null || it.sport == filter }
            .sortedByDescending { it.startTime }
            .map { it.toCard(profile, formatter, prs[it.id].orEmpty()) }
        FeedUiState(
            scope = selected.scope,
            isLoading = false,
            cards = cards,
            sports = if (sports.size > 1) sports.sortedBy { it.ordinal } else emptyList(),
            sportFilter = filter,
            hasAnyActivity = activities.isNotEmpty(),
            message = selected.message,
            hasUnreadNotifications = !selected.notificationsSeen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())

    /** Switches between the Following and You feeds. */
    fun selectScope(scope: FeedScope) = selection.update { it.copy(scope = scope) }

    /** Shows only [sport], or every sport for null. */
    fun selectSport(sport: SportType?) = selection.update { it.copy(sport = sport) }

    /** Permanently deletes activity [id] and its streams. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val deleted = activityRepository.delete(id)
            selection.update { it.copy(message = if (deleted) "Activity deleted" else "That activity was already deleted") }
        }
    }

    /** The bell was opened: its unread dot goes. */
    fun notificationsOpened() = selection.update { it.copy(notificationsSeen = true) }

    /** Clears the one-shot message after the snackbar showed it. */
    fun messageShown() = selection.update { it.copy(message = null) }

    private fun SavedActivity.toCard(profile: UserProfile, formatter: UnitFormatter, prs: List<PersonalBest>) = ActivityCardUi(
        id = id,
        athleteName = profile.displayName,
        athletePhotoPath = profile.photoPath,
        meta = listOf(timeFormatter.format(startTime), profile.location.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
        visibilityLabel = visibilityLabel(visibility),
        sport = sport,
        title = title,
        description = description.trim().takeIf { it.isNotEmpty() },
        stats = activityStats(this, formatter),
        route = if (sport.usesGps || polyline != null) decodeRoute(polyline) else null,
        highFiveCount = 0,
        achievements = prs.maxByOrNull { it.target }?.let { Achievements(prs.size, ProgressFormat.effortLabel(it.target, formatter)) },
    )

    private data class Selection(
        val scope: FeedScope = FeedScope.Following,
        val sport: SportType? = null,
        val message: String? = null,
        val notificationsSeen: Boolean = false,
    )

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Lock label for activities that are not public, null for public ones. */
        fun visibilityLabel(visibility: Visibility): String? = when (visibility) {
            Visibility.EVERYONE -> null
            Visibility.FOLLOWERS -> "Followers"
            Visibility.ONLY_ME -> "Only you"
        }
    }
}
