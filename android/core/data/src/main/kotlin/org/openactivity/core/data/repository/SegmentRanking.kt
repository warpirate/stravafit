// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

/**
 * Places for segment efforts, frozen when an effort is saved (Strava's rule): the new effort is compared
 * with every effort already stored, all-time and within its calendar year. An earlier effort that is as
 * fast keeps the higher place. Pure functions.
 */
object SegmentRanking {
    /** An effort already stored, as ranking needs it. */
    data class Entry(val elapsedMillis: Long, val year: Int)

    /**
     * The frozen places of a new effort.
     *
     * @property rankAllTime 1-based place among all efforts, null when there were none before.
     * @property rankYear 1-based place among efforts of the same year, null when none that year before.
     * @property isPersonalRecord faster than every earlier effort (and there was at least one).
     */
    data class Places(val rankAllTime: Int?, val rankYear: Int?, val isPersonalRecord: Boolean)

    /** Places of an effort of [elapsedMillis] in [year] against the [earlier] ones. */
    fun place(earlier: List<Entry>, elapsedMillis: Long, year: Int): Places {
        val allTime = if (earlier.isEmpty()) null else earlier.count { it.elapsedMillis <= elapsedMillis } + 1
        val sameYear = earlier.filter { it.year == year }
        val yearly = if (sameYear.isEmpty()) null else sameYear.count { it.elapsedMillis <= elapsedMillis } + 1
        return Places(allTime, yearly, isPersonalRecord = allTime == 1)
    }

    /**
     * Places for a batch of new efforts saved one after the other (laps of one activity, or a re-match that
     * replays activities in date order): each one is ranked against [existing] plus the ones before it.
     */
    fun placeInOrder(existing: List<Entry>, new: List<Entry>): List<Places> {
        val seen = ArrayList(existing)
        return new.map { entry ->
            place(seen, entry.elapsedMillis, entry.year).also { seen += entry }
        }
    }
}
