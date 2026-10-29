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
import kotlinx.coroutines.launch
import org.openactivity.app.data.FeedRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.Activity
import org.openactivity.app.model.Visibility
import org.openactivity.app.ui.common.activityStats
import java.util.Locale
import javax.inject.Inject

/**
 * Feed screen state: activities for the selected [FeedScope], newest first (ADR-013), formatted
 * in the viewer's units.
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    preferencesRepository: PreferencesRepository,
    private val timeFormatter: RelativeTimeFormatter,
) : ViewModel() {

    private val scope = MutableStateFlow(FeedScope.Following)

    val uiState: StateFlow<FeedUiState> = combine(
        feedRepository.activities,
        preferencesRepository.preferences,
        scope,
    ) { activities, preferences, selected ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        FeedUiState(
            scope = selected,
            isLoading = false,
            cards = activities
                .filter { selected == FeedScope.Following || it.isOwn }
                .sortedByDescending { it.startTime }
                .map { it.toCard(formatter) },
            unreadNotifications = SAMPLE_UNREAD,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())

    /** Switches between the Following and You feeds. */
    fun selectScope(newScope: FeedScope) {
        scope.value = newScope
    }

    /** Gives or removes a high-five. Updates optimistically through the repository. */
    fun toggleHighFive(activityId: String) {
        viewModelScope.launch { feedRepository.toggleHighFive(activityId) }
    }

    private fun Activity.toCard(formatter: UnitFormatter) = ActivityCardUi(
        id = id,
        athleteName = athlete.name,
        meta = "${timeFormatter.format(startTime)} · ${athlete.location}",
        visibilityLabel = visibility.takeIf { isOwn && it != Visibility.Everyone }?.label,
        withLine = withLine(withAthletes),
        sport = sport,
        title = title,
        description = description,
        stats = activityStats(this, formatter),
        route = route?.takeIf { sport.usesGps && it.size > 1 },
        achievementText = achievementText(achievementCount, topAchievement),
        highFiverNames = highFivers,
        highFiveCount = highFiveCount,
        highFiveGiven = highFiveGiven,
        commentCount = commentCount,
        isOwn = isOwn,
    )

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SAMPLE_UNREAD = 2
        private const val NAMED_COMPANIONS = 2

        /** "with Sam, Li +1 other" (home-feed detail 2.8). */
        fun withLine(names: List<String>): String? {
            if (names.isEmpty()) return null
            val firstNames = names.map { it.substringBefore(' ') }
            val shown = firstNames.take(NAMED_COMPANIONS).joinToString(", ")
            val rest = names.size - NAMED_COMPANIONS
            return when {
                rest <= 0 -> "with $shown"
                rest == 1 -> "with $shown +1 other"
                else -> "with $shown +$rest others"
            }
        }

        fun achievementText(count: Int, top: String?): String? = when {
            count <= 0 -> null
            top == null -> if (count == 1) "1 achievement" else "$count achievements"
            count == 1 -> top
            else -> "$count achievements · $top"
        }
    }
}
