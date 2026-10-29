// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ModelTest {
    @Test
    fun `sport list matches the documented 56 types with unique api names`() {
        assertEquals(56, SportType.entries.size)
        assertEquals(SportType.entries.size, SportType.entries.map { it.apiName }.toSet().size)
    }

    @Test
    fun `api name lookup is case insensitive`() {
        assertEquals(SportType.TRAIL_RUN, SportType.fromApiName("trailrun"))
        assertEquals(SportType.E_MOUNTAIN_BIKE_RIDE, SportType.fromApiName(" EMountainBikeRide "))
        assertNull(SportType.fromApiName("Quidditch"))
    }

    @Test
    fun `display flags follow the sport`() {
        assertTrue(SportType.RUN.displaysPace)
        assertFalse(SportType.RIDE.displaysPace)
        assertEquals(SpeedDisplay.PACE_PER_100M, SportType.SWIM.speedDisplay)
        assertEquals(SpeedDisplay.PACE_PER_500M, SportType.ROWING.speedDisplay)
        assertFalse(SportType.YOGA.usesGps)
        assertTrue(SportType.VIRTUAL_RIDE.isVirtual)
        assertTrue(SportType.GRAVEL_RIDE.isRideLike)
        assertTrue(SportType.TRAIL_RUN.isRunLike)
        assertFalse(SportType.WALK.isRunLike)
    }

    @Test
    fun `visibility defaults to followers and round trips`() {
        assertEquals(Visibility.FOLLOWERS, Visibility.DEFAULT)
        Visibility.entries.forEach { assertEquals(it, Visibility.fromApiName(it.apiName)) }
    }

    @Test
    fun `stream keys round trip`() {
        StreamType.entries.forEach { assertEquals(it, StreamType.fromKey(it.key)) }
    }

    @Test
    fun `distance and speed conversions`() {
        assertEquals(1.0, Distance.miles(1.0).miles, 1e-12)
        assertEquals(5.0, Distance.kilometers(5.0).kilometers, 1e-12)
        val fivePerKm = Speed.fromPace(5.minutes, Distance.kilometers(1.0))
        assertEquals(1000.0 / 300.0, fivePerKm.metersPerSecond, 1e-12)
        assertEquals(5.minutes, fivePerKm.pace(UnitSystem.METRIC))
        assertEquals(12.0, Speed(10.0 / 3.0).kilometersPerHour, 1e-9)
        assertNull(Speed.ZERO.pace(UnitSystem.IMPERIAL))
        assertEquals(Speed.ZERO, Speed.of(Distance(10.0), Duration.ZERO))
    }

    @Test
    fun `summary rejects moving time above elapsed`() {
        assertFailsWith<IllegalArgumentException> {
            ActivitySummary(
                localId = "x", sportType = SportType.RUN, name = "n", startEpochMillis = 0, timeZoneId = "UTC",
                distance = Distance.ZERO, movingTime = 10.seconds, elapsedTime = 5.seconds,
                elevationGain = Distance.ZERO, elevationLoss = Distance.ZERO, maxSpeed = Speed.ZERO,
            )
        }
    }

    @Test
    fun `lat lng validates ranges`() {
        assertFailsWith<IllegalArgumentException> { LatLng(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { LatLng(0.0, 180.5) }
    }

    @Test
    fun `zero length lap is flagged`() {
        val lap = Lap(0, 1.seconds, Duration.ZERO, Duration.ZERO, Distance.ZERO, Distance.ZERO, LapTrigger.MANUAL)
        assertTrue(lap.isZeroLength)
    }
}
