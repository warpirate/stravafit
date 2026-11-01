// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A training goal: reach [target] of [metric] in every [period].
 *
 * @property sport `SportType` name, or null for all sports combined.
 * @property period `WEEK`, `MONTH` or `YEAR`.
 * @property metric `DISTANCE` (metres), `TIME` (moving seconds), `ELEVATION` (metres gained) or `COUNT`
 *   (activities).
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: String?,
    val period: String,
    val metric: String,
    val target: Double,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
