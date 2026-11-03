// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.segment

import org.junit.jupiter.api.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** Test-only geometry around a park in Zurich; nothing here ships in the app. */
internal object SegmentFixtures {
    val origin = LatLng(47.3700, 8.5400)

    fun north(meters: Double, east: Double = 0.0, from: LatLng = origin): LatLng =
        Spherical.destination(Spherical.destination(from, 0.0, meters), 90.0, east)

    /** A straight segment [length] metres due north of [origin]. */
    fun straight(length: Double = 500.0): SegmentShape = SegmentShape(listOf(origin, north(length / 2), north(length)))

    /**
     * A track from a list of positions, one point every [secondsPerPoint], with recorded distance measured
     * point to point.
     */
    fun track(points: List<LatLng>, secondsPerPoint: Double = 1.0, paused: Set<Int> = emptySet()): SegmentTrack {
        val distance = DoubleArray(points.size)
        for (i in 1 until points.size) distance[i] = distance[i - 1] + Spherical.distance(points[i - 1], points[i])
        return SegmentTrack(
            latitudes = DoubleArray(points.size) { points[it].latitude },
            longitudes = DoubleArray(points.size) { points[it].longitude },
            offsetMillis = LongArray(points.size) { (it * secondsPerPoint * 1000).toLong() },
            distanceMeters = distance,
            paused = BooleanArray(points.size) { it in paused },
        )
    }

    /** Points every [step] metres north from [from] to [to] (either direction), [east] metres off the line. */
    fun run(from: Double, to: Double, step: Double = 5.0, east: Double = 0.0): List<LatLng> {
        val n = (kotlin.math.abs(to - from) / step).toInt()
        val sign = if (to >= from) 1 else -1
        return (0..n).map { north(from + sign * it * step, east) }
    }
}

class SegmentMatcherTest {
    private val f = SegmentFixtures

    @Test
    fun `an exact pass matches once with the time between the gates`() {
        val track = f.track(f.run(-200.0, 700.0))
        val matches = SegmentMatcher.match(track, f.straight())
        assertEquals(1, matches.size)
        val m = matches.single()
        // 5 m per second, start gate on point 40, finish gate on point 140.
        assertEquals(40, m.startIndex)
        assertEquals(140, m.endIndex)
        assertEquals(100_000.0, m.elapsedMillis, 50.0)
        assertEquals(500.0, m.distanceMeters, 2.0)
        assertTrue(m.onTrackFraction >= 0.99)
    }

    @Test
    fun `gate times are interpolated between samples`() {
        // Points every 10 m starting 3 m before the start: the start lies 30% into the first leg.
        val points = (0..80).map { f.north(-3.0 + it * 10.0) }
        val m = SegmentMatcher.match(f.track(points, secondsPerPoint = 2.0), f.straight()).single()
        assertEquals(600.0, m.startOffsetMillis, 20.0)
        // Finish at 500 m: 50.3 legs in, at 2 s each.
        assertEquals(100_600.0, m.endOffsetMillis, 20.0)
        assertEquals(100_000.0, m.elapsedMillis, 40.0)
    }

    @Test
    fun `a partial pass does not count`() {
        assertTrue(SegmentMatcher.match(f.track(f.run(-100.0, 320.0)), f.straight()).isEmpty())
        // Joining part way along and finishing does not count either.
        assertTrue(SegmentMatcher.match(f.track(f.run(150.0, 700.0)), f.straight()).isEmpty())
    }

    @Test
    fun `the wrong direction does not count`() {
        assertTrue(SegmentMatcher.match(f.track(f.run(700.0, -200.0)), f.straight()).isEmpty())
    }

    @Test
    fun `leaving the line part way does not count`() {
        val out = f.run(-50.0, 150.0) + f.run(150.0, 350.0, east = 80.0) + f.run(350.0, 600.0)
        assertTrue(SegmentMatcher.match(f.track(out), f.straight()).isEmpty())
    }

    @Test
    fun `GPS wobble inside the corridor still counts`() {
        val random = Random(7)
        val wobbly = f.run(-100.0, 600.0).mapIndexed { i, _ -> f.north(-100.0 + i * 5.0, east = random.nextDouble(-15.0, 15.0)) }
        assertEquals(1, SegmentMatcher.match(f.track(wobbly), f.straight()).size)
    }

    @Test
    fun `out and back laps each count once`() {
        val laps = f.run(-50.0, 550.0) + f.run(550.0, -50.0) + f.run(-50.0, 550.0)
        val matches = SegmentMatcher.match(f.track(laps), f.straight())
        assertEquals(2, matches.size)
        assertTrue(matches[0].endIndex < matches[1].startIndex)
    }

    @Test
    fun `laps of a loop segment each count`() {
        val side = 150.0
        val corners = listOf(f.origin, f.north(0.0, side), f.north(side, side), f.north(side, 0.0), f.origin)
        val loop = SegmentShape(corners)
        val lap = corners.zipWithNext().flatMap { (a, b) ->
            val d = Spherical.distance(a, b)
            val bearing = Spherical.initialBearing(a, b)
            (0 until (d / 5).toInt()).map { Spherical.destination(a, bearing, it * 5.0) }
        }
        val three = List(3) { lap }.flatten() + listOf(f.origin)
        val matches = SegmentMatcher.match(f.track(three), loop)
        assertEquals(3, matches.size)
        matches.forEach { assertEquals(120_000.0, it.elapsedMillis, 1_500.0) }
    }

    @Test
    fun `paused time is left out of moving time`() {
        val points = f.run(-50.0, 600.0)
        val paused = (60..69).toSet()
        val m = SegmentMatcher.match(f.track(points, paused = paused), f.straight()).single()
        assertEquals(m.elapsedMillis - 10_000.0, m.movingMillis, 1.0)
    }

    @Test
    fun `a far away track is dropped by the box test`() {
        val elsewhere = f.run(-200.0, 700.0).map { LatLng(it.latitude + 1.0, it.longitude) }
        assertTrue(SegmentMatcher.match(f.track(elsewhere), f.straight()).isEmpty())
    }

    @Test
    fun `20k points against 200 segments match well under a second`() {
        // A 100 km meander: 20,000 points 5 m apart, zig-zagging north over a 10 km wide area.
        val random = Random(42)
        val points = ArrayList<LatLng>(20_000)
        var p = f.origin
        var bearing = 10.0
        repeat(20_000) {
            points += p
            bearing += random.nextDouble(-8.0, 8.0)
            p = Spherical.destination(p, bearing, 5.0)
        }
        val track = f.track(points)
        // 150 segments cut from the track itself (so matching really runs) plus 50 scattered ones nearby.
        val shapes = (0 until 150).map { k ->
            val s = 100 + k * 130
            SegmentShape(points.subList(s, s + 100 + (k % 5) * 40).filterIndexed { i, _ -> i % 4 == 0 })
        } + (0 until 50).map { k ->
            val start = Spherical.destination(f.origin, k * 7.0, 500.0 + k * 150.0)
            SegmentShape(listOf(start, Spherical.destination(start, 45.0, 800.0)))
        }
        // Warm the JIT once so the timing measures matching, not class loading.
        shapes.take(5).forEach { SegmentMatcher.match(track, it) }
        var found = 0
        val time = measureTime { shapes.forEach { found += SegmentMatcher.match(track, it).size } }
        assertTrue(found >= 150, "every segment cut from the track matches (found $found)")
        assertTrue(time.inWholeMilliseconds < 1_000, "matching took $time")
    }
}
