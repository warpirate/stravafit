// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class SegmentRowsTest {
    private fun effort(rankAllTime: Int?, rankYear: Int?, pr: Boolean = false) = SegmentEffort(
        id = 1, segmentId = 9, segmentName = "Riverside", segmentSport = SportType.RUN, segmentDistanceMeters = 520.0,
        segmentGradePercent = 0.4, activityId = 3, activityTitle = "Morning Run", activitySport = SportType.RUN,
        startTime = Instant.parse("2026-10-01T07:00:00Z"), timeZone = ZoneOffset.UTC, year = 2026, startIndex = 0, endIndex = 10,
        elapsed = 102.seconds, moving = 102.seconds, distanceMeters = 521.0, averageSpeedMps = 5.1, averageHeartRate = null,
        maxHeartRate = null, averagePower = null, averageCadence = null, rankAllTime = rankAllTime, rankYear = rankYear,
        isPersonalRecord = pr,
    )

    @Test
    fun `rows show time, distance, grade and pace with the frozen medal`() {
        Locale.setDefault(Locale.US)
        val row = DetailRows.segments(listOf(effort(1, 1, pr = true)), UnitSystem.METRIC, SpeedDisplay.PACE).single()
        assertEquals("1:42", row.time)
        assertTrue(row.detail, row.detail.startsWith("0.52 km · 0.4%"))
        assertEquals(1, row.rank)
        assertEquals("PR", row.medalLabel)
    }

    @Test
    fun `a yearly medal is outlined and a fourth place has none`() {
        val yearly = DetailRows.segments(listOf(effort(5, 2)), UnitSystem.METRIC, SpeedDisplay.PACE).single()
        assertEquals(2, yearly.rank)
        assertTrue(yearly.yearly)
        assertEquals("2nd this year", yearly.medalLabel)
        val none = DetailRows.segments(listOf(effort(4, 4)), UnitSystem.METRIC, SpeedDisplay.PACE).single()
        assertNull(none.rank)
        assertNull(none.medalLabel)
        val first = DetailRows.segments(listOf(effort(null, null)), UnitSystem.METRIC, SpeedDisplay.PACE).single()
        assertNull("a first effort earns nothing", first.rank)
    }
}
