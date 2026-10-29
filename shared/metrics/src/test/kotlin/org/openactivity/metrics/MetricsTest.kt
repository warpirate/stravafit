// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.metrics

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Builds 1 Hz samples from a list of (seconds, speed m/s) phases. */
private fun phases(vararg p: Pair<Int, Double>, altitude: ((Double) -> Double)? = null): ActivitySamples {
    val time = ArrayList<Double>()
    val dist = ArrayList<Double>()
    var t = 0.0
    var d = 0.0
    time += t
    dist += d
    for ((seconds, speed) in p) repeat(seconds) {
        t += 1.0
        d += speed
        time += t
        dist += d
    }
    val alt = altitude?.let { f -> dist.map(f).toDoubleArray() }
    return ActivitySamples(time.toDoubleArray(), dist.toDoubleArray(), altitude = alt)
}

class ActivitySamplesTest {
    @Test
    fun `rejects inconsistent input`() {
        assertFailsWith<IllegalArgumentException> { ActivitySamples(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0)) }
        assertFailsWith<IllegalArgumentException> { ActivitySamples(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { ActivitySamples(doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 0.0)) }
    }

    @Test
    fun `duration conversion round trips`() {
        assertEquals(1.5, secondsToDuration(1.5).toSecondsDouble(), 1e-12)
    }

    @Test
    fun `cumulative distance and pace helpers`() {
        val pts = (0..10).map { Spherical.destination(LatLng(0.0, 0.0), 90.0, it * 100.0) }
        val cum = DistanceMetrics.cumulative(pts)
        assertEquals(1000.0, cum.last(), 1e-6)
        assertEquals(1000.0, DistanceMetrics.total(pts), 1e-6)
        assertEquals(300.0, SpeedMath.paceSeconds(1000.0 / 300.0)!!, 1e-9)
        assertNull(SpeedMath.paceSeconds(0.0))
        assertEquals(4.0, SpeedMath.speedFromPace(250.0), 1e-12)
        assertEquals(0.0, SpeedMath.averageSpeed(10.0, 0.0))
    }
}

class MovingTimeTest {
    private val run = MovingTimeRule.forSport(SportType.RUN)

    @Test
    fun `stop is excluded from moving time`() {
        val s = phases(300 to 3.0, 60 to 0.0, 300 to 3.0)
        assertEquals(600.0, MovingTime.movingSeconds(s, run), 3.0)
    }

    @Test
    fun `slow hike is still moving under the hike rule but not the run rule`() {
        val s = phases(600 to 0.7)
        assertEquals(600.0, MovingTime.movingSeconds(s, MovingTimeRule.forSport(SportType.HIKE)), 1e-9)
        assertEquals(0.0, MovingTime.movingSeconds(s, run), 1e-9)
    }

    @Test
    fun `paused intervals never count`() {
        val base = phases(100 to 3.0)
        val paused = BooleanArray(base.size) { it in 41..60 }
        val s = ActivitySamples(base.time, base.distance, paused = paused)
        assertEquals(80.0, MovingTime.movingSeconds(s, run), 1e-9)
        assertEquals(80.0, MovingTime.sumIntervals(s, MovingTime.timerMask(s)), 1e-9)
    }

    @Test
    fun `long gap is judged on its own implied speed`() {
        val moving = ActivitySamples(doubleArrayOf(0.0, 1.0, 2.0, 142.0, 143.0), doubleArrayOf(0.0, 15.0, 30.0, 2130.0, 2145.0))
        assertEquals(143.0, MovingTime.movingSeconds(moving, MovingTimeRule.forSport(SportType.RIDE)), 1e-9)
        val stopped = ActivitySamples(doubleArrayOf(0.0, 1.0, 2.0, 142.0, 143.0), doubleArrayOf(0.0, 15.0, 30.0, 40.0, 55.0))
        assertEquals(3.0, MovingTime.movingSeconds(stopped, MovingTimeRule.forSport(SportType.RIDE)), 1e-9)
    }

    @Test
    fun `degenerate inputs give zero`() {
        assertEquals(0.0, MovingTime.movingSeconds(ActivitySamples(DoubleArray(0), DoubleArray(0)), run))
        val same = ActivitySamples(doubleArrayOf(5.0, 5.0, 5.0), doubleArrayOf(0.0, 0.0, 0.0))
        assertEquals(0.0, MovingTime.movingSeconds(same, run))
    }
}

class ElevationTest {
    @Test
    fun `steady climb counts nearly all of it`() {
        val alt = DoubleArray(401) { it * 0.5 } // 0..200 m
        val t = ElevationMetrics.compute(alt, ElevationSource.BAROMETER.hysteresisMeters)
        assertTrue(t.gain in 196.0..200.0, "gain ${t.gain}")
        assertEquals(0.0, t.loss)
    }

    @Test
    fun `noise below the hysteresis gives no gain`() {
        val rnd = Random(3)
        val alt = DoubleArray(5000) { 100.0 + rnd.nextDouble(-1.5, 1.5) }
        val t = ElevationMetrics.compute(alt, 2.0)
        assertEquals(0.0, t.gain)
        assertEquals(0.0, t.loss)
    }

    @Test
    fun `climb then descent is symmetric`() {
        val alt = DoubleArray(2001) { 50.0 * sin(it / 2000.0 * Math.PI) + 0.0 }
        val t = ElevationMetrics.compute(alt, 2.0, 1)
        assertEquals(50.0, t.gain, 2.0)
        assertEquals(50.0, t.loss, 2.0)
    }

    @Test
    fun `streaming equals batch and survives snapshots`() {
        val rnd = Random(11)
        val alt = DoubleArray(3000) { it * 0.1 + rnd.nextDouble(-3.0, 3.0) }
        val batch = ElevationMetrics.compute(alt, 2.0)
        val a = ElevationAccumulator(2.0)
        alt.take(1500).forEach(a::add)
        val b = ElevationAccumulator(2.0).apply { restore(a.snapshot()) }
        alt.drop(1500).forEach(b::add)
        assertEquals(batch.gain, b.gain, 0.0)
        assertEquals(batch.loss, b.loss, 0.0)
    }

    @Test
    fun `nan samples are skipped and pressure conversion round trips`() {
        val acc = ElevationAccumulator(2.0)
        acc.add(Double.NaN)
        assertTrue(acc.smoothed.isNaN())
        acc.add(10.0)
        assertEquals(10.0, acc.smoothed)
        assertEquals(0.0, ElevationMetrics.pressureToAltitude(ElevationMetrics.STANDARD_SEA_LEVEL_HPA), 1e-9)
        assertEquals(1234.0, ElevationMetrics.pressureToAltitude(ElevationMetrics.altitudeToPressure(1234.0)), 1e-6)
        assertEquals(110.9, ElevationMetrics.pressureToAltitude(1000.0), 0.5)
    }
}

class SplitsTest {
    @Test
    fun `constant speed splits with a partial`() {
        val s = phases(625 to 4.0, altitude = { it * 0.01 }) // 2500 m at 4 m/s, 1 % grade
        val splits = Splits.compute(s, 1000.0, MovingTime.timerMask(s))
        assertEquals(3, splits.size)
        assertEquals(250.0, splits[0].elapsedTime.toSecondsDouble(), 1e-9)
        assertEquals(250.0, splits[1].movingTime.toSecondsDouble(), 1e-9)
        assertEquals(10.0, splits[1].elevationDelta!!.meters, 1e-9)
        assertTrue(splits[2].isPartial)
        assertEquals(500.0, splits[2].distance.meters, 1e-9)
        assertEquals(125.0, splits[2].elapsedTime.toSecondsDouble(), 1e-9)
        assertEquals(listOf(0, 1, 2), splits.map { it.index })
    }

    @Test
    fun `stop inside a split counts as elapsed but not moving`() {
        val s = phases(100 to 5.0, 40 to 0.0, 100 to 5.0)
        val mask = MovingTime.movingMask(s, MovingTimeRule.forSport(SportType.RUN))
        val splits = Splits.compute(s, 1000.0, mask)
        assertEquals(1, splits.size)
        assertEquals(240.0, splits[0].elapsedTime.toSecondsDouble(), 1e-9)
        assertEquals(200.0, splits[0].movingTime.toSecondsDouble(), 5.0)
        assertNull(splits[0].elevationDelta)
    }

    @Test
    fun `one sample jumping several boundaries yields several splits`() {
        val acc = SplitAccumulator(1000.0)
        acc.add(0.0, 0.0, 0.0)
        val out = acc.add(300.0, 3000.0, 300.0)
        assertEquals(3, out.size)
        out.forEach { assertEquals(100.0, it.elapsedTime.toSecondsDouble(), 1e-9) }
        assertEquals(3, acc.finish().size)
    }

    @Test
    fun `snapshot restore continues identically`() {
        val s = phases(700 to 3.3)
        val full = Splits.compute(s, 1000.0, MovingTime.timerMask(s))
        val a = SplitAccumulator(1000.0)
        for (i in 0 until 350) a.add(s.time[i], s.distance[i], s.time[i])
        val b = SplitAccumulator(1000.0).apply { restore(a.snapshot()) }
        for (i in 350 until s.size) b.add(s.time[i], s.distance[i], s.time[i])
        assertEquals(full, b.finish())
    }
}

class BestEffortsTest {
    @Test
    fun `injected sprint is found exactly`() {
        // 4 km at 4:30/km, 1 km at 3:00/km, 5 km at 4:30/km, sampled at 1 Hz.
        val slow = 1000.0 / 270.0
        val fast = 1000.0 / 180.0
        val s = phases(1080 to slow, 180 to fast, 1350 to slow)
        val best = assertNotNull(BestEfforts.forDistance(s, 1000.0))
        assertEquals(180.0, best.elapsedSeconds, 1e-6)
        assertTrue(abs(best.startDistance - 4000.0) < 10.0, "start ${best.startDistance}")
    }

    @Test
    fun `irregular sampling still gives the exact window`() {
        val t = doubleArrayOf(0.0, 7.0, 9.5, 31.0, 40.0, 100.0)
        val s = ActivitySamples(t, DoubleArray(t.size) { t[it] * 20.0 }) // 2 km at 20 m/s
        assertEquals(50.0, BestEfforts.forDistance(s, 1000.0)!!.elapsedSeconds, 1e-9)
        assertEquals(20.0, BestEfforts.forDistance(s, 400.0)!!.elapsedSeconds, 1e-9)
        assertNull(BestEfforts.forDistance(s, 5000.0))
    }

    @Test
    fun `longer efforts never take less time`() {
        val rnd = Random(5)
        val time = DoubleArray(4000) { it.toDouble() }
        val dist = DoubleArray(4000)
        for (i in 1 until 4000) dist[i] = dist[i - 1] + rnd.nextDouble(2.0, 5.0)
        val s = ActivitySamples(time, dist)
        val efforts = BestEfforts.forDistances(s, StandardEfforts.RUN_DISTANCES_M)
        assertTrue(efforts.size >= 6)
        efforts.zipWithNext().forEach { (a, b) -> assertTrue(a.elapsedSeconds <= b.elapsedSeconds) }
    }

    @Test
    fun `vehicle-speed jump splits windows`() {
        // 600 m at 3 m/s, then a 2 km "jump" in 10 s, then 600 m at 3 m/s.
        val s = phases(200 to 3.0, 10 to 200.0, 200 to 3.0)
        assertNotNull(BestEfforts.forDistance(s, 1000.0))
        assertNull(BestEfforts.forDistance(s, 1000.0, maxSpeedMps = 12.0))
        assertEquals(133.333, BestEfforts.forDistance(s, 400.0, maxSpeedMps = 12.0)!!.elapsedSeconds, 1e-3)
    }

    @Test
    fun `duration efforts and max speed`() {
        val s = phases(600 to 5.0, 60 to 12.0, 600 to 5.0)
        assertEquals(720.0, BestEfforts.forDuration(s, 60.0)!!.distanceMeters, 1e-9)
        assertEquals(12.0, BestEfforts.maxAverageSpeed(s, 5.0), 1e-9)
        assertEquals(5.0 * 540 + 720.0, BestEfforts.forDuration(s, 600.0)!!.distanceMeters, 1e-9)
        assertNull(BestEfforts.forDuration(s, 10_000.0))
        assertEquals(0.0, BestEfforts.maxAverageSpeed(ActivitySamples(DoubleArray(1), DoubleArray(1)), 5.0))
    }

    @Test
    fun `standard lists are ascending`() {
        listOf(StandardEfforts.RUN_DISTANCES_M, StandardEfforts.RIDE_DISTANCES_M, StandardEfforts.RIDE_DURATIONS_S)
            .forEach { l -> assertEquals(l.sorted(), l) }
        assertEquals(14, StandardEfforts.RUN_DISTANCES_M.size)
    }
}

class GradeAdjustedPaceTest {
    @Test
    fun `cost model shape`() {
        assertEquals(3.6, RunningCost.FLAT, 1e-12)
        assertEquals(1.0, RunningCost.factor(0.0), 1e-12)
        assertTrue(RunningCost.factor(0.10) > 1.5)
        val floor = RunningCost.minetti(-0.09)
        assertEquals(floor, RunningCost.cost(-0.15), 1e-12)
        assertEquals(floor, RunningCost.cost(-0.20), 1e-12)
        assertEquals(RunningCost.FLAT, RunningCost.cost(-0.45), 1e-12)
        assertEquals(RunningCost.FLAT, RunningCost.cost(-0.9), 1e-12)
        assertEquals(RunningCost.minetti(-0.05), RunningCost.cost(-0.05), 1e-12)
        assertEquals(RunningCost.cost(0.45), RunningCost.cost(1.0), 1e-12)
        // Never cheaper than the floor on any descent.
        var g = 0.0
        while (g >= -0.45) {
            assertTrue(RunningCost.cost(g) >= floor - 1e-12)
            g -= 0.01
        }
    }

    @Test
    fun `flat run has gap equal to pace and climbs are faster`() {
        val flat = phases(600 to 3.0, altitude = { 100.0 })
        val mask = MovingTime.timerMask(flat)
        assertEquals(3.0, GradeAdjustedPace.averageSpeed(flat, mask)!!, 1e-9)
        val hill = phases(600 to 3.0, altitude = { it * 0.05 })
        val gap = GradeAdjustedPace.averageSpeed(hill, MovingTime.timerMask(hill))!!
        assertEquals(GradeAdjustedPace.adjustedSpeed(3.0, 0.05), gap, 1e-6)
        assertNull(GradeAdjustedPace.averageSpeed(phases(10 to 3.0), BooleanArray(11) { it > 0 }))
    }

    @Test
    fun `smoothed grade reads the slope`() {
        val dist = DoubleArray(200) { it * 2.0 }
        val alt = DoubleArray(200) { it * 2.0 * 0.08 }
        val grade = GradeAdjustedPace.smoothedGrade(dist, alt)
        grade.forEach { assertEquals(0.08, it, 1e-9) }
    }
}

class ZonesAndStatsTest {
    @Test
    fun `default zones from max heart rate`() {
        val z = HeartRateZones.fromMaxHeartRate(200)
        assertEquals(listOf(130, 162, 178, 194), z.upperBounds)
        assertEquals(5, z.count)
        assertEquals(0, z.zoneOf(129.0))
        assertEquals(0, z.zoneOf(130.0))
        assertEquals(1, z.zoneOf(131.0))
        assertEquals(4, z.zoneOf(199.0))
        assertFailsWith<IllegalArgumentException> { HeartRateZones(listOf(150, 140)) }
    }

    @Test
    fun `time in zones uses sample and hold and skips dropouts`() {
        val time = doubleArrayOf(0.0, 10.0, 20.0, 30.0, 100.0, 110.0)
        val hr = doubleArrayOf(120.0, 150.0, Double.NaN, 170.0, 300.0, 190.0)
        val s = ActivitySamples(time, DoubleArray(time.size) { it * 10.0 }, heartRate = hr)
        val out = TimeInZones.compute(s, HeartRateZones.fromMaxHeartRate(200))
        assertContentEquals(doubleArrayOf(10.0, 10.0, 0.0, 0.0, 0.0), out)
        val masked = TimeInZones.compute(s, HeartRateZones.fromMaxHeartRate(200), BooleanArray(6) { it != 1 })
        assertContentEquals(doubleArrayOf(0.0, 10.0, 0.0, 0.0, 0.0), masked)
    }

    @Test
    fun `averages and maxima`() {
        val time = doubleArrayOf(0.0, 1.0, 3.0, 4.0)
        val v = doubleArrayOf(100.0, 200.0, Double.NaN, 50.0)
        assertEquals((100.0 + 400.0) / 3.0, SeriesStats.timeWeightedAverage(time, v)!!, 1e-12)
        assertNull(SeriesStats.timeWeightedAverage(doubleArrayOf(0.0), doubleArrayOf(1.0)))
        assertEquals(200.0, SeriesStats.max(v))
        assertNull(SeriesStats.max(doubleArrayOf(Double.NaN)))
    }
}
