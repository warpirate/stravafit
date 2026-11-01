// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedPreferenceTest {
    @Test
    fun `defaults follow the sport`() {
        assertEquals(SpeedDisplay.PACE, SpeedDisplays.resolve(SportType.RUN, UnitSystem.METRIC))
        assertEquals(SpeedDisplay.PACE, SpeedDisplays.resolve(SportType.WALK, UnitSystem.IMPERIAL))
        assertEquals(SpeedDisplay.PACE, SpeedDisplays.resolve(SportType.HIKE, UnitSystem.METRIC))
        assertEquals(SpeedDisplay.SPEED, SpeedDisplays.resolve(SportType.RIDE, UnitSystem.METRIC))
        assertEquals(SpeedDisplay.PACE_PER_500M, SpeedDisplays.resolve(SportType.ROWING, UnitSystem.IMPERIAL))
    }

    @Test
    fun `swims are per 100 m in metric and per 100 yd in imperial unless chosen`() {
        assertEquals(SpeedDisplay.PACE_PER_100M, SpeedDisplays.resolve(SportType.SWIM, UnitSystem.METRIC))
        assertEquals(SpeedDisplay.PACE_PER_100YD, SpeedDisplays.resolve(SportType.SWIM, UnitSystem.IMPERIAL))
        assertEquals(
            SpeedDisplay.PACE_PER_100M,
            SpeedDisplays.resolve(SportType.SWIM, UnitSystem.IMPERIAL, swimUnit = SwimPaceUnit.METERS),
        )
        assertEquals(
            SpeedDisplay.PACE_PER_100YD,
            SpeedDisplays.resolve(SportType.SWIM, UnitSystem.METRIC, swimUnit = SwimPaceUnit.YARDS),
        )
    }

    @Test
    fun `overrides switch between pace and speed`() {
        assertEquals(SpeedDisplay.SPEED, SpeedDisplays.resolve(SportType.RUN, UnitSystem.METRIC, PaceOrSpeed.SPEED))
        assertEquals(SpeedDisplay.PACE, SpeedDisplays.resolve(SportType.RIDE, UnitSystem.METRIC, PaceOrSpeed.PACE))
        assertEquals(SpeedDisplay.SPEED, SpeedDisplays.resolve(SportType.SWIM, UnitSystem.METRIC, PaceOrSpeed.SPEED))
        // Asking for pace on a sport that already shows one keeps its own unit.
        assertEquals(
            SpeedDisplay.PACE_PER_100YD,
            SpeedDisplays.resolve(SportType.SWIM, UnitSystem.IMPERIAL, PaceOrSpeed.PACE),
        )
    }

    @Test
    fun `pace unit lengths`() {
        assertEquals(1000.0, SpeedDisplays.paceUnitMeters(SpeedDisplay.PACE, UnitSystem.METRIC))
        assertEquals(1609.344, SpeedDisplays.paceUnitMeters(SpeedDisplay.PACE, UnitSystem.IMPERIAL))
        assertEquals(91.44, SpeedDisplays.paceUnitMeters(SpeedDisplay.PACE_PER_100YD, UnitSystem.METRIC)!!, 1e-9)
        assertEquals(500.0, SpeedDisplays.paceUnitMeters(SpeedDisplay.PACE_PER_500M, UnitSystem.METRIC))
        assertNull(SpeedDisplays.paceUnitMeters(SpeedDisplay.SPEED, UnitSystem.METRIC))
    }

    @Test
    fun `elapsed time headlines gym, studio, racket and rest-heavy sports`() {
        listOf(
            SportType.YOGA, SportType.WEIGHT_TRAINING, SportType.WORKOUT, SportType.CROSSFIT,
            SportType.HIGH_INTENSITY_INTERVAL_TRAINING, SportType.ALPINE_SKI, SportType.SNOWBOARD,
            SportType.SURFING, SportType.TENNIS, SportType.ROCK_CLIMBING,
        ).forEach { assertTrue(it.headlinesElapsedTime, it.name) }
        listOf(SportType.RUN, SportType.RIDE, SportType.WALK, SportType.HIKE, SportType.SWIM, SportType.NORDIC_SKI)
            .forEach { assertFalse(it.headlinesElapsedTime, it.name) }
    }

    @Test
    fun `unknown or invalid visibility fails safe to only me`() {
        assertEquals(Visibility.ONLY_ME, Visibility.parseOrPrivate(null))
        assertEquals(Visibility.ONLY_ME, Visibility.parseOrPrivate(""))
        assertEquals(Visibility.ONLY_ME, Visibility.parseOrPrivate("public_to_aliens"))
        assertEquals(Visibility.EVERYONE, Visibility.parseOrPrivate("everyone"))
        assertEquals(Visibility.FOLLOWERS, Visibility.parseOrPrivate("followers_only"))
        assertEquals(Visibility.FOLLOWERS, Visibility.parseOrPrivate("FOLLOWERS"))
    }
}
