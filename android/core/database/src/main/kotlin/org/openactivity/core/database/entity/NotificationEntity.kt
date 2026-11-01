// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry of the notifications inbox, produced on the device (new best efforts, goals, weekly
 * summaries, imports, recovered recordings).
 *
 * @property type what happened, as a stable name (see the data layer's `NotificationType`).
 * @property target where a tap leads: `ACTIVITY` (then [activityId]), `GOALS`, `PROGRESS` or `ACTIVITIES`.
 * @property activityId the activity it is about; the entry goes when that activity is deleted.
 * @property dedupeKey identifies the event, so it is stored (and announced) only once.
 */
@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["dedupe_key"], unique = true),
        Index(value = ["activity_id"]),
        Index(value = ["created_at_millis"]),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String,
    val target: String,
    @ColumnInfo(name = "activity_id") val activityId: Long?,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
)
