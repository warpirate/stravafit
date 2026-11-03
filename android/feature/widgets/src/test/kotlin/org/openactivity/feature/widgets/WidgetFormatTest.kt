// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WidgetFormatTest {
    @Test
    fun `clock has no leading zero and grows to hours`() {
        assertEquals("5:02", WidgetFormat.clock(5.minutes + 2.seconds))
        assertEquals("59:59", WidgetFormat.clock(59.minutes + 59.seconds))
        assertEquals("1:00:00", WidgetFormat.clock(1.hours))
    }

    @Test
    fun `live pace per km and per mile`() {
        assertEquals("5:12", WidgetFormat.speedValue(1000.0 / 312, SportType.RUN, UnitSystem.METRIC, Locale.US))
        assertEquals("8:22", WidgetFormat.speedValue(1609.344 / 502, SportType.RUN, UnitSystem.IMPERIAL, Locale.US))
    }

    @Test
    fun `stopped or crawling shows dashes, never a huge pace`() {
        assertEquals(WidgetFormat.NO_PACE, WidgetFormat.speedValue(0.0, SportType.RUN, UnitSystem.METRIC))
        assertEquals(WidgetFormat.NO_PACE, WidgetFormat.speedValue(Double.NaN, SportType.WALK, UnitSystem.METRIC))
        assertEquals(WidgetFormat.NO_PACE, WidgetFormat.speedValue(1000.0 / (30 * 60 + 1), SportType.WALK, UnitSystem.METRIC))
        assertEquals("30:00", WidgetFormat.speedValue(1000.0 / (30 * 60), SportType.WALK, UnitSystem.METRIC, Locale.US))
    }

    @Test
    fun `rides show speed, zero when stopped`() {
        assertEquals("27.0", WidgetFormat.speedValue(7.5, SportType.RIDE, UnitSystem.METRIC, Locale.US))
        assertEquals("0.0", WidgetFormat.speedValue(0.0, SportType.RIDE, UnitSystem.METRIC, Locale.US))
    }

    @Test
    fun `distance follows the locale decimal separator`() {
        assertEquals("7.89", WidgetFormat.distance(7890.0, UnitSystem.METRIC, Locale.US))
        assertEquals("7,89", WidgetFormat.distance(7890.0, UnitSystem.METRIC, Locale.GERMANY))
    }

    @Test
    fun `spoken clock reads as words`() {
        val spoken = { d: kotlin.time.Duration ->
            WidgetFormat.spokenClock(d, { "$it hours" }, { "$it minutes" }, { "$it seconds" })
        }
        assertEquals("25 minutes 2 seconds", spoken(25.minutes + 2.seconds))
        assertEquals("1 hours 0 minutes 5 seconds", spoken(1.hours + 5.seconds))
        assertEquals("9 seconds", spoken(9.seconds))
    }
}
