// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import java.time.Instant

/** What an inbox entry is about. Stored by name, so entries are never renamed. */
enum class NotificationType {
    /** An all-time first place on a best effort. */
    PERSONAL_RECORD,

    /** A 2nd or 3rd best effort, all-time or this year. */
    BEST_EFFORT,
    GOAL_REACHED,

    /** The one-off "80% there" nudge for a goal period. */
    GOAL_REMINDER,
    WEEKLY_SUMMARY,
    IMPORT_FINISHED,
    IMPORT_FAILED,

    /** An activity saved from a recording that was interrupted and recovered. */
    RECORDING_RECOVERED,

    /** An automatic backup could not be written. */
    BACKUP_FAILED,
}

/** Where tapping an inbox entry leads. */
enum class NotificationTarget {
    /** The entry's activity. */
    ACTIVITY,

    /** The goals list. */
    GOALS,

    /** You, Progress. */
    PROGRESS,

    /** You, Activities. */
    ACTIVITIES,

    /** The weekly recap screen of the week named in the entry's dedupe key (`weekly:2026-10-26`). */
    RECAP,
    /** Settings (backups and other app setup). */
    SETTINGS,
}

/**
 * One inbox entry.
 *
 * @property dedupeKey the event it records (see [NewNotification.dedupeKey]); some destinations read it.
 */
data class InboxNotification(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val target: NotificationTarget,
    val activityId: Long?,
    val createdAt: Instant,
    val isRead: Boolean,
    val dedupeKey: String = "",
)

/**
 * An entry to add to the inbox.
 *
 * @property dedupeKey names the event, e.g. `goal:3:2026-10-26:reached`; an event is recorded only once.
 */
data class NewNotification(
    val type: NotificationType,
    val title: String,
    val body: String,
    val target: NotificationTarget,
    val dedupeKey: String,
    val activityId: Long? = null,
)
