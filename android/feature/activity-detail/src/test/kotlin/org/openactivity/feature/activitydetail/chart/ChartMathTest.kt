// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.model.SportType

class ChartMathTest {

    private fun point(i: Int, distance: Double, speed: Double, hr: Int? = 150, elevation: Double? = 100.0 + i, paused: Boolean = false) =
        ActivityPoint(
            latitude = 47.0 + i * 1e-5, longitude = 8.0, elevationMeters = elevation, epochMillis = 1000L * i,
            offsetMillis = 1000L * i, heartRate = hr, cadence = null, powerWatts = null, speedMps = speed,
            accuracyMeters = 4.0, distanceMeters = distance, paused = paused, segment = 0,
        )

    @Test
    fun `moving average is centred, time based and keeps gaps`() {
        val time = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val values = doubleArrayOf(1.0, 2.0, Double.NaN, 4.0, 5.0)
        val out = ChartMath.movingAverage(time, values, windowSeconds = 2.0)
        assertEquals(1.5, out[0], 1e-9) // 1, 2
        assertEquals(1.5, out[1], 1e-9) // 1, 2, (NaN skipped)
        assertTrue(out[2].isNaN())
        assertEquals(4.5, out[3], 1e-9) // (NaN), 4, 5
        assertEquals(4.5, out[4], 1e-9)
    }

    @Test
    fun `buckets cover every sample exactly once`() {
        val buckets = ChartMath.buckets(1003, 300)
        assertEquals(300, buckets.size)
        assertEquals(0, buckets.first().first)
        assertEquals(1002, buckets.last().last)
        buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertEquals(5, ChartMath.buckets(5, 300).size)
    }

    @Test
    fun `nearest index clamps and rounds to the closer sample`() {
        val x = doubleArrayOf(0.0, 10.0, 20.0, 30.0)
        assertEquals(0, ChartMath.nearestIndex(x, -5.0))
        assertEquals(3, ChartMath.nearestIndex(x, 99.0))
        assertEquals(1, ChartMath.nearestIndex(x, 14.9))
        assertEquals(2, ChartMath.nearestIndex(x, 15.1))
        assertEquals(-1, ChartMath.nearestIndex(DoubleArray(0), 1.0))
    }

    @Test
    fun `nice ticks use round steps and cover the range`() {
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0), ChartMath.niceTicks(0.3, 5.6, 4))
        val ticks = ChartMath.niceTicks(412.0, 447.0, 3)
        assertTrue(ticks.first() <= 412.0 && ticks.last() >= 447.0)
        val step = ticks[1] - ticks[0]
        assertTrue(step in listOf(10.0, 20.0, 25.0, 50.0))
        assertTrue(ChartMath.niceTicks(Double.NaN, 1.0).isEmpty())
    }

    @Test
    fun `build uses a distance axis, hides stops and drops implausible heart rates`() {
        val points = List(600) { i ->
            val stopped = i in 200..259
            point(i, distance = i * 3.0, speed = if (stopped) 0.0 else 3.0, hr = if (i == 10) 300 else 150)
        }
        val data = ChartMath.build(points, SportType.RUN, maxSamples = 600, speedWindowSeconds = 0.0)!!
        assertEquals(ChartAxis.DISTANCE, data.axis)
        assertEquals(1797.0, data.xMax, 1e-9)
        val speed = data.seriesFor(ChartMetric.SPEED)!!
        assertTrue(speed.values[230].isNaN())
        assertEquals(3.0, speed.values[100], 1e-9)
        assertTrue(data.seriesFor(ChartMetric.HEART_RATE)!!.values[10].isNaN())
        assertEquals(150.0, data.seriesFor(ChartMetric.HEART_RATE)!!.average!!, 1e-9)
        assertEquals(600, data.positions.size)
    }

    @Test
    fun `indoor sessions fall back to a time axis and downsampling keeps the length`() {
        val points = List(3600) { i -> point(i, distance = 0.0, speed = 0.0, elevation = null) }
        val data = ChartMath.build(points, SportType.WORKOUT, maxSamples = 300)!!
        assertEquals(ChartAxis.TIME, data.axis)
        assertEquals(300, data.x.size)
        assertNull(data.seriesFor(ChartMetric.SPEED))
        assertNull(data.seriesFor(ChartMetric.ELEVATION))
        assertNotNull(data.seriesFor(ChartMetric.HEART_RATE))
    }

    @Test
    fun `touch position maps to the sample under the finger`() {
        val points = List(101) { i -> point(i, distance = i * 10.0, speed = 3.0) }
        val data = ChartMath.build(points, SportType.RUN, maxSamples = 200)!!
        assertEquals(50, ChartMath.indexAtTouch(data, 500f, 1000f))
        assertEquals(0, ChartMath.indexAtTouch(data, -20f, 1000f))
        assertEquals(100, ChartMath.indexAtTouch(data, 1200f, 1000f))
    }

    @Test
    fun `fewer than two points give no chart`() {
        assertNull(ChartMath.build(listOf(point(0, 0.0, 0.0)), SportType.RUN))
    }

    @Test
    fun `pace ticks land on round paces inside the speed range`() {
        // 2.7..3.4 m/s is 6:10..4:54 per km: 30 s steps give 5:00, 5:30 and 6:00.
        val ticks = ChartMath.paceTicks(2.7..3.4, unitMeters = 1000.0)
        assertEquals(listOf(300.0, 330.0, 360.0), ticks.map { Math.round(1000.0 / it).toDouble() }.sorted())
        assertTrue(ticks.all { it in 2.7..3.4 })
    }

    @Test
    fun `pace ticks widen the step to stay within the tick budget`() {
        // 1.5..4.0 m/s is 11:07..4:10 per km: whole minutes give too many ticks, two minutes give 6:00, 8:00, 10:00.
        val paces = ChartMath.paceTicks(1.5..4.0, unitMeters = 1000.0, maxTicks = 3).map { Math.round(1000.0 / it) }.sorted()
        assertEquals(listOf(360L, 480L, 600L), paces)
    }

    @Test
    fun `pace ticks handle a range that reaches zero speed`() {
        val ticks = ChartMath.paceTicks(-0.2..3.0, unitMeters = 1000.0)
        assertTrue(ticks.isNotEmpty())
        assertTrue(ticks.all { it > 0.0 && it <= 3.0 })
        assertTrue(ChartMath.paceTicks(-1.0..0.0, unitMeters = 1000.0).isEmpty())
    }

    @Test
    fun `scaled ticks are round in the display unit`() {
        // 5.5..8.5 m/s is 19.8..30.6 km/h.
        val kmh = ChartMath.scaledTicks(5.5..8.5, factor = 3.6).map { it * 3.6 }
        assertTrue(kmh.isNotEmpty())
        kmh.forEach { assertEquals(Math.round(it).toDouble(), it, 1e-9) }
        assertTrue(kmh.any { it in 19.8..30.6 })
    }

    @Test
    fun `pace ticks never exceed the budget for a crawl`() {
        // 0.05 m/s at the top: even ten-minute steps would give dozens of ticks.
        assertTrue(ChartMath.paceTicks(-0.01..0.05, unitMeters = 1000.0, maxTicks = 3).size <= 3)
    }

    @Test
    fun `imperial elevation ticks are round feet`() {
        val factor = org.openactivity.feature.activitydetail.format.DetailFormat.elevationDisplayFactor(org.openactivity.model.UnitSystem.IMPERIAL)
        val feet = ChartMath.scaledTicks(100.0..160.0, factor).map { it * factor }
        assertTrue(feet.isNotEmpty())
        feet.forEach { assertEquals(Math.round(it / 50.0) * 50.0, it, 1e-6) }
    }
}
