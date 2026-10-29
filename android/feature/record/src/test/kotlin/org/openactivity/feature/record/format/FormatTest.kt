// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.format

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.recordingSnapshot
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormatTest {
    private lateinit var saved: Locale

    @Before fun setUp() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun tearDown() = Locale.setDefault(saved)

    @Test fun durations() {
        assertEquals("0:07", RecordFormat.duration(7.seconds))
        assertEquals("12:47", RecordFormat.duration(12.minutes + 47.seconds))
        assertEquals("1:02:03", RecordFormat.duration(1.hours + 2.minutes + 3.seconds))
    }

    @Test fun distancesAndElevation() {
        assertEquals("2.41", RecordFormat.distance(2410.0, UnitSystem.METRIC))
        assertEquals("1.00", RecordFormat.distance(1609.344, UnitSystem.IMPERIAL))
        assertEquals("1,234", RecordFormat.elevation(1234.0, UnitSystem.METRIC))
        assertEquals("328", RecordFormat.elevation(100.0, UnitSystem.IMPERIAL))
    }

    @Test fun paceHidesStandstillAndCrawl() {
        assertEquals("5:00", RecordFormat.pace(1000.0 / 300, SpeedDisplay.PACE, UnitSystem.METRIC))
        assertEquals("2:00", RecordFormat.pace(100.0 / 120, SpeedDisplay.PACE_PER_100M, UnitSystem.METRIC))
        assertNull(RecordFormat.pace(0.0, SpeedDisplay.PACE, UnitSystem.METRIC))
        assertNull(RecordFormat.pace(0.2, SpeedDisplay.PACE, UnitSystem.METRIC))
        assertEquals("/mi", RecordFormat.paceUnit(SpeedDisplay.PACE, UnitSystem.IMPERIAL))
    }

    @Test fun speeds() {
        assertEquals("36.0", RecordFormat.speed(10.0, UnitSystem.METRIC))
        assertEquals("22.4", RecordFormat.speed(10.0, UnitSystem.IMPERIAL))
        assertEquals("0.0", RecordFormat.speed(Double.NaN, UnitSystem.METRIC))
    }

    @Test fun runPaceDefaultsToTheSplitAverage() {
        val m = LiveMetrics.from(recordingSnapshot(), PaceMode.SPLIT)
        assertEquals("Split pace", m.speed.label)
        assertEquals("5:17", m.speed.value) // 410 m in 2:10
        assertEquals("/km", m.speed.unit)
        assertEquals("12:47", m.time.value)
        assertEquals("2.41", m.distance.value)
        assertEquals("Split 3", m.split.label)
    }

    @Test fun timeShowsTimerTimeSoItFreezesWhilePaused() {
        val paused = recordingSnapshot(status = RecordingStatus.PAUSED).copy(elapsed = 3.minutes, timerTime = 2.minutes + 26.seconds)
        assertEquals("2:26", LiveMetrics.from(paused, PaceMode.SPLIT).time.value)
    }

    @Test fun runPaceCanShowCurrentOrAverage() {
        assertEquals("Pace", LiveMetrics.from(recordingSnapshot(), PaceMode.CURRENT).speed.label)
        assertEquals("Avg pace", LiveMetrics.from(recordingSnapshot(), PaceMode.AVERAGE).speed.label)
        val paused = LiveMetrics.from(recordingSnapshot(status = RecordingStatus.PAUSED), PaceMode.CURRENT)
        assertNull("no current pace while paused", paused.speed.value)
    }

    @Test fun rideShowsCurrentSpeedMovingAndAverageWhenPaused() {
        val moving = LiveMetrics.from(recordingSnapshot(sport = SportType.RIDE), PaceMode.SPLIT)
        assertEquals("Speed", moving.speed.label)
        assertEquals("km/h", moving.speed.unit)
        val paused = LiveMetrics.from(recordingSnapshot(sport = SportType.RIDE, status = RecordingStatus.AUTO_PAUSED), PaceMode.SPLIT)
        assertEquals("Avg speed", paused.speed.label)
    }
}
