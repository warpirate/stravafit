// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.EffortMedal
import org.openactivity.core.data.model.EffortRecord
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.model.SportType
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Gold, silver and bronze for best efforts (profile-progress B1/B5, activity-detail 6.1/6.2).
 *
 * Within a target a better value wins (a shorter time for [EffortKind.DISTANCE], a longer distance for
 * [EffortKind.DURATION]); on a tie the earlier activity keeps the higher place. Pure functions.
 */
object EffortRanking {
    /** Places that earn a medal. */
    const val PODIUM: Int = 3

    /** Run types share running benchmarks. */
    val RUN_POOL: Set<SportType> = SportType.entries.filter { it.isRunLike }.toSet()

    /** Every kind of ride shares cycling benchmarks. */
    val RIDE_POOL: Set<SportType> = SportType.entries.filter { it.isRideLike }.toSet()

    /**
     * The sports an effort of [sport] is ranked against: all run types together, all ride types together,
     * otherwise the sport alone. You, Best efforts uses the same pools, so a medal and the list agree.
     */
    fun poolOf(sport: SportType): Set<SportType> = when {
        sport.isRunLike -> RUN_POOL
        sport.isRideLike -> RIDE_POOL
        else -> setOf(sport)
    }

    /** Best first; ties go to the earlier activity. */
    fun comparator(kind: EffortKind): Comparator<EffortRecord> {
        val byValue: Comparator<EffortRecord> = if (kind == EffortKind.DISTANCE) {
            compareBy { it.value }
        } else {
            compareByDescending { it.value }
        }
        return byValue.thenBy { it.startEpochMillis }.thenBy { it.activityId }
    }

    /**
     * Today's top [perTarget] per target, ordered by target and then by rank, as [PersonalBest]s carrying
     * their rank. With [year], only efforts from that year count.
     */
    fun podium(
        records: List<EffortRecord>,
        kind: EffortKind,
        perTarget: Int = PODIUM,
        year: Int? = null,
    ): List<PersonalBest> = records
        .filter { year == null || it.year == year }
        .groupBy { targetKey(it.target) }
        .toSortedMap()
        .flatMap { (_, list) ->
            list.sortedWith(comparator(kind)).take(perTarget).mapIndexed { i, r ->
                PersonalBest(
                    target = r.target,
                    value = r.value,
                    activityId = r.activityId,
                    activityTitle = r.activityTitle,
                    sport = r.sport,
                    achievedAt = Instant.ofEpochMilli(r.startEpochMillis),
                    rank = i + 1,
                )
            }
        }

    /**
     * The medals of every activity as of the moment it was recorded: activity id to the efforts that took
     * an all-time or a yearly place 1 to 3, by target. Activities without a medal are absent.
     */
    fun medalsByActivity(records: List<EffortRecord>, kind: EffortKind): Map<Long, List<EffortMedal>> {
        val result = HashMap<Long, MutableList<EffortMedal>>()
        val chronological = compareBy<EffortRecord> { it.startEpochMillis }.thenBy { it.activityId }
        val better = comparator(kind)
        for ((_, list) in records.groupBy { targetKey(it.target) }) {
            val allTime = ArrayList<EffortRecord>(PODIUM + 1)
            val byYear = HashMap<Int, ArrayList<EffortRecord>>()
            for (r in list.sortedWith(chronological)) {
                val yearPodium = byYear.getOrPut(r.year) { ArrayList(PODIUM + 1) }
                val allTimeRank = placeAmong(allTime, r, better)
                val yearRank = placeAmong(yearPodium, r, better)
                if (allTimeRank != null || yearRank != null) {
                    result.getOrPut(r.activityId) { mutableListOf() } += EffortMedal(r.target, r.value, allTimeRank, yearRank, r.year)
                }
                insert(allTime, r, better)
                insert(yearPodium, r, better)
            }
        }
        result.values.forEach { medals -> medals.sortBy { it.target } }
        return result
    }

    /** The medals of one activity (see [medalsByActivity]). */
    fun medalsOf(records: List<EffortRecord>, kind: EffortKind, activityId: Long): List<EffortMedal> {
        val mine = records.filter { it.activityId == activityId }.map { targetKey(it.target) }.toSet()
        if (mine.isEmpty()) return emptyList()
        return medalsByActivity(records.filter { targetKey(it.target) in mine }, kind)[activityId].orEmpty()
    }

    /** Targets within half a metre (or second) of each other are the same benchmark. */
    fun sameTarget(a: Double, b: Double): Boolean = abs(a - b) < 0.5

    /**
     * 1-based place of [r] behind the earlier efforts on [podium] (best first, at most [PODIUM] long), or
     * null when it is off the podium. Earlier efforts that are as good rank ahead.
     */
    private fun placeAmong(podium: List<EffortRecord>, r: EffortRecord, better: Comparator<EffortRecord>): Int? {
        val ahead = podium.count { better.compare(it, r) < 0 }
        return (ahead + 1).takeIf { it <= PODIUM }
    }

    private fun insert(podium: ArrayList<EffortRecord>, r: EffortRecord, better: Comparator<EffortRecord>) {
        podium += r
        podium.sortWith(better)
        while (podium.size > PODIUM) podium.removeAt(podium.lastIndex)
    }

    private fun targetKey(target: Double): Long = (target * 2).roundToLong()
}
