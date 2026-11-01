// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.FeedSeenRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.model.UserProfile
import org.openactivity.app.ui.common.activityStats
import org.openactivity.app.ui.common.decodeRoute
import org.openactivity.app.feature.me.ProgressFormat
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.model.SportType
import org.openactivity.model.Visibility
import java.util.Locale
import javax.inject.Inject

/**
 * Feed screen state. The You feed lists the activities stored on this device, newest first
 * (ADR-013), formatted in the viewer's units; the Following feed needs a server account for other
 * athletes, so for now it shows yours, with the items you have not seen in the feed yet first
 * (home-feed 1.3). Cards carry their attached photos for the map-and-photos carousel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    preferencesRepository: PreferencesRepository,
    private val timeFormatter: RelativeTimeFormatter,
    extrasRepository: ActivityExtrasRepository,
    private val seenRepository: FeedSeenRepository,
) : ViewModel() {

    private val selection = MutableStateFlow(Selection())

    /** Personal records by activity id, over runs and rides separately. */
    private val records = combine(
        activityRepository.observePersonalBests(ProgressFormat.RUN_SPORTS),
        activityRepository.observePersonalBests(ProgressFormat.RIDE_SPORTS),
    ) { runs, rides -> (runs + rides).distinctBy { it.activityId to it.target }.groupBy { it.activityId } }

    /** Attached photos by activity id; activities without photos are left out. */
    private val photos: Flow<Map<Long, List<ActivityPhoto>>> = activityRepository.observeActivities()
        .map { list -> list.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(ids.map { id -> extrasRepository.observe(id).map { id to it.photos } }) { pairs ->
                    pairs.filter { it.second.isNotEmpty() }.toMap()
                }
            }
        }
        // Cards never wait for photos, and an unreadable photo index only costs the carousel.
        .onStart { emit(emptyMap()) }
        .catch { emit(emptyMap()) }

    private val base = combine(
        activityRepository.observeActivities(),
        profileRepository.profile,
        preferencesRepository.preferences,
        selection,
        records,
    ) { activities, profile, preferences, selected, prs -> Base(activities, profile, preferences, selected, prs) }

    val uiState: StateFlow<FeedUiState> = combine(base, photos, seenRepository.seenThroughId) { b, photoMap, seenThrough ->
        val formatter = UnitFormatter(b.preferences.unitSystem, Locale.getDefault())
        val sports = b.activities.map { it.sport }.distinct()
        val filter = b.selected.sport?.takeIf { it in sports }
        // Following is you plus the athletes you follow; without a server account that is only you.
        val newestFirst = b.activities
            .filter { filter == null || it.sport == filter }
            .sortedByDescending { it.startTime }
        // Following puts unseen items first (each group newest first); You is always plain chronological.
        val cutoff = seenThrough ?: Long.MAX_VALUE
        val unseen = if (b.selected.scope == FeedScope.Following) newestFirst.filter { it.id > cutoff } else emptyList()
        val unseenIds = unseen.mapTo(HashSet()) { it.id }
        val ordered = unseen + newestFirst.filter { it.id !in unseenIds }
        val cards = ordered.map {
            it.toCard(b.profile, formatter, b.prs[it.id].orEmpty(), photoMap[it.id].orEmpty(), isNew = it.id in unseenIds)
        }
        FeedUiState(
            scope = b.selected.scope,
            isLoading = false,
            cards = cards,
            sports = if (sports.size > 1) sports.sortedBy { it.ordinal } else emptyList(),
            sportFilter = filter,
            hasAnyActivity = b.activities.isNotEmpty(),
            message = b.selected.message,
            hasUnreadNotifications = !b.selected.notificationsSeen,
            unseenCount = unseen.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())

    init {
        // First run of the feed: what is already on the device counts as seen, so an app update does
        // not suddenly mark a whole history as new.
        viewModelScope.launch {
            if (seenRepository.seenThroughId.first() == null) {
                seenRepository.markSeenThrough(activityRepository.observeActivities().first().maxOfOrNull { it.id } ?: 0L)
            }
        }
    }

    /**
     * The viewer has looked at the feed: everything in it now counts as seen. Called when the feed
     * leaves the screen, so items do not jump between groups while they are being read.
     */
    fun markAllSeen() {
        viewModelScope.launch {
            activityRepository.observeActivities().first().maxOfOrNull { it.id }?.let { seenRepository.markSeenThrough(it) }
        }
    }

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

    private fun SavedActivity.toCard(
        profile: UserProfile,
        formatter: UnitFormatter,
        prs: List<PersonalBest>,
        photos: List<ActivityPhoto>,
        isNew: Boolean,
    ) = ActivityCardUi(
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
        photos = photos,
        isNew = isNew,
    )

    private data class Base(
        val activities: List<SavedActivity>,
        val profile: UserProfile,
        val preferences: UserPreferences,
        val selected: Selection,
        val prs: Map<Long, List<PersonalBest>>,
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
