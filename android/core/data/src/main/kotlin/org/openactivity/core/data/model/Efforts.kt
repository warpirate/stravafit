// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import org.openactivity.model.SportType

/**
 * One best effort of one activity, as the ranking needs it.
 *
 * @property value seconds for distance efforts (lower is better), metres for duration efforts (higher is better).
 * @property year the calendar year the activity started in, in its own time zone.
 */
data class EffortRecord(
    val activityId: Long,
    val target: Double,
    val value: Double,
    val startEpochMillis: Long,
    val year: Int,
    val sport: SportType,
    val activityTitle: String,
)

/**
 * A best effort's place among your own efforts over the same target.
 *
 * Ranks are "as of" the activity: it is compared with itself and the efforts recorded before it, so an
 * activity keeps the medal it earned when it was recorded. Only places 1 to 3 are medals.
 *
 * @property allTimeRank 1..3 among all earlier efforts, else null.
 * @property yearRank 1..3 among earlier efforts of the same calendar [year], else null.
 */
data class EffortMedal(
    val target: Double,
    val value: Double,
    val allTimeRank: Int?,
    val yearRank: Int?,
    val year: Int,
) {
    /** The medal to show: the all-time one (solid), else the yearly one (outlined). */
    val rank: Int? get() = allTimeRank ?: yearRank

    /** True when only the yearly ranking earned a medal. */
    val isYearly: Boolean get() = allTimeRank == null && yearRank != null

    /** True for a personal record (all-time first place). */
    val isPersonalRecord: Boolean get() = allTimeRank == 1
}
