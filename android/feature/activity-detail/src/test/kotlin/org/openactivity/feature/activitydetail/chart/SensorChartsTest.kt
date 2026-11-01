// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.model.SportType

class SensorChartsTest {

    private fun point(
        i: Int,
        cadence: Double? = null,
        power: Int? = null,
        elevation: Double? = null,
        paused: Boolean = false,
        speed: Double = 3.0,
    ) = ActivityPoint(
        latitude = 47.0 + i * 1e-5, longitude = 8.0, elevationMeters = elevation, epochMillis = 1000L * i,
        offsetMillis = 1000L * i, heartRate = null, cadence = cadence, powerWatts = power, speedMps = speed,
        accuracyMeters = 4.0, distanceMeters = 3.0 * i, paused = paused, segment = 0,
    )

    @Test
    fun `cadence and power charts exist only when the samples carry them`() {
        val bare = ChartMath.build(List(200) { point(it) }, SportType.RIDE)!!
        assertNull(bare.seriesFor(ChartMetric.CADENCE))
        assertNull(bare.seriesFor(ChartMetric.POWER))

        val sensors = ChartMath.build(List(200) { point(it, cadence = 88.0, power = 210) }, SportType.RIDE)!!
        assertEquals(88.0, sensors.seriesFor(ChartMetric.CADENCE)!!.average!!, 1e-9)
        assertEquals(210.0, sensors.seriesFor(ChartMetric.POWER)!!.average!!, 1e-9)
    }

    @Test
    fun `a sensor that only ever read zero draws no chart, and coasting is left out of the cadence average`() {
        val zeros = ChartMath.build(List(200) { point(it, cadence = 0.0, power = 0) }, SportType.RIDE)!!
        assertNull(zeros.seriesFor(ChartMetric.CADENCE))
        assertNull(zeros.seriesFor(ChartMetric.POWER))

        val coasting = ChartMath.build(List(200) { point(it, cadence = if (it % 2 == 0) 0.0 else 90.0, power = 200) }, SportType.RIDE)!!
        assertEquals(90.0, coasting.seriesFor(ChartMetric.CADENCE)!!.average!!, 1e-9)
    }

    @Test
    fun `sensor samples taken while paused or implausible are gaps`() {
        val points = List(200) { i ->
            point(i, cadence = if (i == 50) 900.0 else 90.0, power = if (i == 60) -5 else 200, paused = i in 100..120)
        }
        val data = ChartMath.build(points, SportType.RIDE, maxSamples = 1000)!!
        val cadence = data.seriesFor(ChartMetric.CADENCE)!!
        val power = data.seriesFor(ChartMetric.POWER)!!
        assertTrue(cadence.values[50].isNaN())
        assertTrue(power.values[60].isNaN())
        assertTrue(cadence.values[110].isNaN())
        assertEquals(90.0, cadence.max, 1e-9)
    }

    @Test
    fun `grade-adjusted speed is faster than speed uphill and only drawn for runs with elevation`() {
        // 10 % climb for the first half, flat for the second half.
        val points = List(400) { i -> point(i, elevation = if (i < 200) 0.3 * i else 60.0) }
        val run = ChartMath.build(points, SportType.RUN, maxSamples = 1000)!!
        val speed = run.seriesFor(ChartMetric.SPEED)!!.values
        val gap = assertNotNull(run.seriesFor(ChartMetric.GRADE_ADJUSTED_SPEED)).values
        assertTrue("uphill GAP should be faster", gap[100] > speed[100] * 1.3)
        assertEquals(speed[350], gap[350], 1e-6)

        assertNull(ChartMath.build(points, SportType.RIDE)!!.seriesFor(ChartMetric.GRADE_ADJUSTED_SPEED))
        assertNull(ChartMath.build(List(400) { point(it) }, SportType.RUN)!!.seriesFor(ChartMetric.GRADE_ADJUSTED_SPEED))
    }

    @Test
    fun `grade adjusted keeps stops as gaps`() {
        val distance = DoubleArray(5) { it * 10.0 }
        val altitude = DoubleArray(5) { it * 1.0 }
        val speed = doubleArrayOf(3.0, Double.NaN, 3.0, 3.0, 3.0)
        val out = ChartMath.gradeAdjusted(distance, altitude, speed)
        assertTrue(out[1].isNaN())
        assertTrue(out[2] > 3.0)
    }

    @Test
    fun `sensor charts get readable minimum spans`() {
        assertEquals(20.0, ChartMath.minSpan(ChartMetric.CADENCE), 0.0)
        assertEquals(50.0, ChartMath.minSpan(ChartMetric.POWER), 0.0)
        assertEquals(ChartMath.minSpan(ChartMetric.SPEED), ChartMath.minSpan(ChartMetric.GRADE_ADJUSTED_SPEED), 0.0)
    }
}
