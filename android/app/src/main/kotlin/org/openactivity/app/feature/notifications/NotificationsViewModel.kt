// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openactivity.app.format.RelativeTimeFormatter
import org.openactivity.core.data.model.InboxNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.repository.NotificationRepository
import java.time.LocalDate
import javax.inject.Inject

/** One row of the inbox, formatted. */
data class InboxItemUi(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    /** "5m", "2h", "3d" or "12 Jun". */
    val age: String,
    val unread: Boolean,
    val destination: InboxDestination,
)

/** Where an inbox row leads. */
sealed interface InboxDestination {
    data class Activity(val id: Long) : InboxDestination

    data object Goals : InboxDestination

    data object Progress : InboxDestination

    data object Activities : InboxDestination

    /** The weekly recap of the week starting [weekStart]. */
    data class Recap(val weekStart: LocalDate) : InboxDestination

    companion object {
        /** The destination of [entry]; an activity entry whose activity is gone falls back to Progress. */
        fun of(entry: InboxNotification): InboxDestination = when (entry.target) {
            NotificationTarget.ACTIVITY -> entry.activityId?.let(::Activity) ?: Progress
            NotificationTarget.GOALS -> Goals
            NotificationTarget.PROGRESS -> Progress
            NotificationTarget.ACTIVITIES -> Activities
            // The recap names its week in the entry's key ("weekly:2026-10-26"); an unreadable key shows Progress.
            NotificationTarget.RECAP -> entry.dedupeKey.removePrefix(WEEKLY_KEY_PREFIX)
                .let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let(::Recap) ?: Progress
        }

        private const val WEEKLY_KEY_PREFIX = "weekly:"
    }
}

/**
 * State of the inbox.
 *
 * @property unreadCount entries not read yet; "Mark all as read" is offered while it is above 0.
 */
data class NotificationsUiState(
    val isLoading: Boolean = true,
    val items: List<InboxItemUi> = emptyList(),
    val unreadCount: Int = 0,
)

/**
 * The notifications inbox: newest first, unread rows tinted, each opening its destination and marking
 * itself read, plus "Mark all as read" (notifications-search N3/N4, improved with per-row read state).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val timeFormatter: RelativeTimeFormatter,
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = repository.observeNotifications().map { entries ->
        NotificationsUiState(
            isLoading = false,
            items = entries.map { it.toUi() },
            unreadCount = entries.count { !it.isRead },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NotificationsUiState())

    private val navigation = Channel<InboxDestination>(Channel.BUFFERED)

    /** Destinations to open, one per tapped row. */
    val destinations: Flow<InboxDestination> = navigation.receiveAsFlow()

    /** A row was tapped: it is read from now on, and its destination opens. */
    fun open(item: InboxItemUi) {
        viewModelScope.launch {
            if (item.unread) repository.markRead(item.id)
            navigation.send(item.destination)
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    private fun InboxNotification.toUi() = InboxItemUi(
        id = id,
        type = type,
        title = title,
        body = body,
        age = timeFormatter.age(createdAt),
        unread = !isRead,
        destination = InboxDestination.of(this),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
