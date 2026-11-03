// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.segment

import org.junit.jupiter.api.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSegmentTrackerTest {
    private val f = SegmentFixtures

    /** Feeds [points] one per second (5 m apart unless given otherwise) and collects every event. */
    private fun feed(tracker: LiveSegmentTracker, points: List<LatLng>, secondsPerPoint: Double = 1.0, from: Long = 0): Pair<List<LiveSegmentEvent>, List<LiveSegmentStatus?>> {
        val events = ArrayList<LiveSegmentEvent>()
        val statuses = ArrayList<LiveSegmentStatus?>()
        var travelled = 0.0
        points.forEachIndexed { i, p ->
            if (i > 0) travelled += Spherical.distance(points[i - 1], p)
            events += tracker.onFix(p, from + (i * secondsPerPoint * 1000).toLong(), travelled, accuracyMeters = 5.0)
            statuses += tracker.status
        }
        return events to statuses
    }

    private fun segment(pr: Long? = 120_000L) = LiveSegment(1, "Hill", f.straight(), pr)

    @Test
    fun `approach, start, ahead of the PR and a new PR at the finish`() {
        val tracker = LiveSegmentTracker(listOf(segment(pr = 120_000L)))
        // 5 m/s: 100 s for 500 m, faster than the 120 s PR.
        val (events, statuses) = feed(tracker, f.run(-400.0, 600.0))
        assertNull(statuses.first(), "400 m away: nothing yet")
        assertIs<LiveSegmentEvent.Approaching>(events[0])
        assertIs<LiveSegmentEvent.Started>(events[1])
        val finished = assertIs<LiveSegmentEvent.Finished>(events[2])
        assertEquals(3, events.size, "one cue each, no repeats")
        assertTrue(finished.isNewPr)
        assertEquals(100_000.0, finished.elapsedMillis.toDouble(), 8_000.0)
        assertTrue(finished.deltaMillis!! < 0)
        val midway = statuses.first { it?.phase == LiveSegmentPhase.ACTIVE && it.fraction in 0.4..0.6 }!!
        assertTrue(midway.deltaMillis!! < 0, "ahead of the PR half way")
        assertEquals(LiveSegmentPhase.FINISHED, statuses.last()?.phase)
    }

    @Test
    fun `slower than the PR is behind and no PR`() {
        val tracker = LiveSegmentTracker(listOf(segment(pr = 60_000L)))
        val (events, statuses) = feed(tracker, f.run(-100.0, 600.0))
        val finished = events.filterIsInstance<LiveSegmentEvent.Finished>().single()
        assertTrue(!finished.isNewPr && finished.deltaMillis!! > 0)
        assertTrue(statuses.filterNotNull().filter { it.phase == LiveSegmentPhase.ACTIVE && it.fraction > 0.3 }.all { it.deltaMillis!! > 0 })
    }

    @Test
    fun `a first effort has no PR to beat`() {
        val tracker = LiveSegmentTracker(listOf(segment(pr = null)))
        val finished = feed(tracker, f.run(-50.0, 600.0)).first.filterIsInstance<LiveSegmentEvent.Finished>().single()
        assertNull(finished.deltaMillis)
        assertTrue(!finished.isNewPr)
    }

    @Test
    fun `leaving the segment abandons the attempt`() {
        val tracker = LiveSegmentTracker(listOf(segment()))
        val path = f.run(-50.0, 200.0) + (1..60).map { f.north(200.0, east = it * 5.0) }
        val events = feed(tracker, path).first
        assertTrue(events.any { it is LiveSegmentEvent.Abandoned })
        assertTrue(events.none { it is LiveSegmentEvent.Finished })
        assertNull(tracker.status)
    }

    @Test
    fun `the result stays on screen for a while then clears`() {
        val tracker = LiveSegmentTracker(listOf(segment()), LiveSegmentConfig(resultMillis = 10_000L))
        val path = f.run(-50.0, 900.0)
        val statuses = feed(tracker, path).second
        val finishIndex = statuses.indexOfFirst { it?.phase == LiveSegmentPhase.FINISHED }
        assertTrue(finishIndex > 0)
        assertEquals(LiveSegmentPhase.FINISHED, statuses[finishIndex + 5]?.phase)
        assertNull(statuses.last())
    }

    @Test
    fun `nothing to watch means nothing happens`() {
        val tracker = LiveSegmentTracker(emptyList())
        assertTrue(feed(tracker, f.run(-50.0, 600.0)).first.isEmpty())
    }
}
