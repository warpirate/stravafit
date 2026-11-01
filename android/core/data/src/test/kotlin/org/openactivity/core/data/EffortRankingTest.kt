// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.EffortRecord
import org.openactivity.core.data.repository.EffortRanking
import org.openactivity.model.SportType

class EffortRankingTest {
    private val day = 86_400_000L

    private fun effort(activity: Long, value: Double, target: Double = 5_000.0, start: Long = activity * day, year: Int = 2026) =
        EffortRecord(activity, target, value, start, year, SportType.RUN, "Run $activity")

    @Test
    fun `the podium keeps the three best per target with their ranks`() {
        val records = listOf(
            effort(1, 1_500.0), effort(2, 1_440.0), effort(3, 1_470.0), effort(4, 1_600.0),
            effort(1, 290.0, target = 1_000.0), effort(2, 280.0, target = 1_000.0),
        )
        val podium = EffortRanking.podium(records, EffortKind.DISTANCE)
        assertEquals(listOf(1_000.0, 1_000.0, 5_000.0, 5_000.0, 5_000.0), podium.map { it.target })
        assertEquals(listOf(1, 2, 1, 2, 3), podium.map { it.rank })
        assertEquals(listOf(2L, 1L, 2L, 3L, 1L), podium.map { it.activityId })
    }

    @Test
    fun `ties go to the earlier activity`() {
        val podium = EffortRanking.podium(listOf(effort(5, 1_500.0), effort(2, 1_500.0)), EffortKind.DISTANCE)
        assertEquals(listOf(2L, 5L), podium.map { it.activityId })
        val medals = EffortRanking.medalsByActivity(listOf(effort(5, 1_500.0), effort(2, 1_500.0)), EffortKind.DISTANCE)
        assertEquals(1, medals.getValue(2).single().allTimeRank)
        assertEquals(2, medals.getValue(5).single().allTimeRank)
    }

    @Test
    fun `a yearly podium only counts that year`() {
        val records = listOf(effort(1, 1_400.0, year = 2025), effort(2, 1_500.0), effort(3, 1_450.0))
        val thisYear = EffortRanking.podium(records, EffortKind.DISTANCE, year = 2026)
        assertEquals(listOf(3L, 2L), thisYear.map { it.activityId })
        assertEquals(listOf(1, 2), thisYear.map { it.rank })
    }

    @Test
    fun `medals are frozen as of each activity`() {
        // 1: first ever (PR). 2: slower (2nd). 3: fastest (new PR). 4: slower than all three (off podium).
        val records = listOf(effort(1, 1_500.0), effort(2, 1_550.0), effort(3, 1_450.0), effort(4, 1_600.0))
        val medals = EffortRanking.medalsByActivity(records, EffortKind.DISTANCE)
        assertEquals(1, medals.getValue(1).single().allTimeRank)
        assertEquals(2, medals.getValue(2).single().allTimeRank)
        assertEquals(1, medals.getValue(3).single().allTimeRank)
        assertTrue(medals.getValue(3).single().isPersonalRecord)
        assertNull(medals[4])
    }

    @Test
    fun `a yearly medal shows when the all-time podium is out of reach`() {
        val records = listOf(
            effort(1, 1_400.0, year = 2025), effort(2, 1_410.0, year = 2025), effort(3, 1_420.0, year = 2025),
            effort(10, 1_500.0, start = 400 * day, year = 2026),
            effort(11, 1_480.0, start = 401 * day, year = 2026),
        )
        val medals = EffortRanking.medalsByActivity(records, EffortKind.DISTANCE)
        val first = medals.getValue(10).single()
        assertNull(first.allTimeRank)
        assertEquals(1, first.yearRank)
        assertEquals(1, first.rank)
        assertTrue(first.isYearly)
        assertFalse(first.isPersonalRecord)
        assertEquals(1, medals.getValue(11).single().yearRank)
    }

    @Test
    fun `duration efforts rank the longest distance first`() {
        val records = listOf(
            effort(1, 3_000.0, target = 720.0),
            effort(2, 3_200.0, target = 720.0),
            effort(3, 3_100.0, target = 720.0),
        )
        assertEquals(listOf(2L, 3L, 1L), EffortRanking.podium(records, EffortKind.DURATION).map { it.activityId })
        val medals = EffortRanking.medalsOf(records, EffortKind.DURATION, 3)
        assertEquals(2, medals.single().allTimeRank)
    }

    @Test
    fun `medals of one activity cover each of its targets`() {
        val records = listOf(
            effort(1, 300.0, target = 1_000.0), effort(1, 1_500.0),
            effort(2, 290.0, target = 1_000.0), effort(2, 1_520.0),
            effort(3, 310.0, target = 1_000.0),
        )
        val medals = EffortRanking.medalsOf(records, EffortKind.DISTANCE, 2)
        assertEquals(listOf(1_000.0, 5_000.0), medals.map { it.target })
        assertEquals(listOf(1, 2), medals.map { it.allTimeRank })
        assertTrue(EffortRanking.medalsOf(records, EffortKind.DISTANCE, 99).isEmpty())
        assertTrue(EffortRanking.sameTarget(21_097.5, 21_097.4))
    }
}
