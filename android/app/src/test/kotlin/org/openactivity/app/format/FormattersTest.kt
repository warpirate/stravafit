// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.testing.TEST_CLOCK
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale

class UnitFormatterTest {
    private val metric = UnitFormatter(UnitSystem.Metric, Locale.UK)
    private val imperial = UnitFormatter(UnitSystem.Imperial, Locale.UK)

    @Test
    fun distance() {
        assertEquals(Measure("8.02", "km", "kilometres"), metric.distance(8_020.0))
        assertEquals(Measure("123.4", "km", "kilometres"), metric.distance(123_400.0))
        assertEquals(Measure("1,204", "km", "kilometres"), metric.distance(1_204_000.0))
        assertEquals(Measure("6.21", "mi", "miles"), imperial.distance(10_000.0))
    }

    @Test
    fun `distance precision follows the rounded value at band edges`() {
        assertEquals("99.99", metric.distance(99_994.0).value)
        assertEquals("100.0", metric.distance(99_996.0).value)
        assertEquals("999.9", metric.distance(999_940.0).value)
        assertEquals("1,000", metric.distance(999_960.0).value)
    }

    @Test
    fun pace() {
        assertEquals(Measure("5:12", "/km", "per kilometre"), metric.pace(10_200.0, 3_184))
        assertEquals("8:22", imperial.pace(10_200.0, 3_184)?.value)
        assertNull(metric.pace(0.0, 100))
        assertEquals("2:00", metric.swimPace(1_500.0, 1_800)?.value)
    }

    @Test
    fun elevationAndGrouping() {
        assertEquals(Measure("1,102", "m", "metres"), metric.elevation(1_102.0))
        assertEquals(Measure("2,664", "ft", "feet"), imperial.elevation(812.0))
        assertEquals("1.204", UnitFormatter(UnitSystem.Metric, Locale.GERMANY).count(1_204))
    }

    @Test
    fun durations() {
        assertEquals("53:04", UnitFormatter.duration(3_184))
        assertEquals("0:42", UnitFormatter.duration(42))
        assertEquals("2h 31m", UnitFormatter.duration(9_060))
        assertEquals("45m", UnitFormatter.compactDuration(2_700))
        assertEquals("108h 0m", UnitFormatter.compactDuration(388_800))
    }
}

class RelativeTimeFormatterTest {
    private val now: ZonedDateTime = ZonedDateTime.now(TEST_CLOCK) // Tue 27 Oct 2026 15:00 IST
    private val format24 = RelativeTimeFormatter(TEST_CLOCK, Locale.UK, use24Hour = true)
    private val format12 = RelativeTimeFormatter(TEST_CLOCK, Locale.US, use24Hour = false)

    private fun ago(duration: Duration): Instant = now.toInstant() - duration

    @Test
    fun `under an hour shows minutes ago`() {
        assertEquals("48 min ago", format24.format(ago(Duration.ofMinutes(48))))
        assertEquals("1 min ago", format24.format(ago(Duration.ofSeconds(5))))
    }

    @Test
    fun `today and yesterday use clock time in the device format`() {
        assertEquals("Today at 10:00", format24.format(ago(Duration.ofHours(5))))
        assertEquals("Today at 10:00 AM", format12.format(ago(Duration.ofHours(5))))
        assertEquals("Yesterday at 22:00", format24.format(ago(Duration.ofHours(17))))
    }

    @Test
    fun `within a week shows the weekday`() {
        assertEquals("Friday at 15:00", format24.format(ago(Duration.ofDays(4))))
    }

    @Test
    fun `older dates add the year only when it differs`() {
        assertEquals("15 Oct · 15:00", format24.format(ago(Duration.ofDays(12))))
        assertEquals("27 Oct 2025 · 15:00", format24.format(ago(Duration.ofDays(365))))
        assertEquals("15 Oct", format24.shortDate(ago(Duration.ofDays(12))))
    }
}
