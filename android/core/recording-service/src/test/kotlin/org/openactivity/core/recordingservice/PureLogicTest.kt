// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.recordingservice.audio.AudioCue
import org.openactivity.core.recordingservice.audio.AudioCuePlanner
import org.openactivity.core.recordingservice.notification.NotificationFormat
import org.openactivity.core.recordingservice.permission.PermissionSnapshot
import org.openactivity.core.recordingservice.permission.PermissionStep
import org.openactivity.core.recordingservice.permission.RecordingPermissionFlow
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingEngine
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PureLogicTest {
    private fun state(status: RecordingStatus, km: Double, moving: Duration = Duration.ZERO): RecordingState =
        RecordingEngine(Corpus.load("S001").config).state.copy(status = status, distanceMeters = km * 1000, movingTime = moving)

    @Test
    fun `milestones fire once per interval with the interval pace`() {
        val planner = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC)
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 0.0)))
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 0.99, 5.minutes)))
        val first = planner.onState(state(RecordingStatus.RECORDING, 1.001, 5.minutes))
        assertEquals(listOf(AudioCue.Milestone(1000.0, 5.minutes, 300.0)), first)
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 1.5, 7.minutes)))
        val second = planner.onState(state(RecordingStatus.RECORDING, 2.0, 11.minutes)).single() as AudioCue.Milestone
        assertEquals(2000.0, second.distanceMeters, 0.0)
        assertEquals(360.0, second.intervalPaceSecondsPerUnit!!, 1e-9)
    }

    @Test
    fun `half-mile interval in imperial units`() {
        val planner = AudioCuePlanner(AudioCueSettings(distanceInterval = 0.5), UnitSystem.IMPERIAL)
        val cue = planner.onState(state(RecordingStatus.RECORDING, 0.81)).single() as AudioCue.Milestone
        assertEquals(804.672, cue.distanceMeters, 1e-6)
    }

    @Test
    fun `pause and auto-pause cues follow the settings`() {
        val planner = AudioCuePlanner(AudioCueSettings(announceAutoPause = false), UnitSystem.METRIC)
        planner.onState(state(RecordingStatus.RECORDING, 0.0))
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.AUTO_PAUSED, 0.0)))
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 0.0)))
        assertEquals(listOf(AudioCue.Paused), planner.onState(state(RecordingStatus.PAUSED, 0.0)))
        assertEquals(listOf(AudioCue.Resumed), planner.onState(state(RecordingStatus.RECORDING, 0.0)))

        val announcing = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC)
        announcing.onState(state(RecordingStatus.RECORDING, 0.0))
        assertEquals(listOf(AudioCue.AutoPaused), announcing.onState(state(RecordingStatus.AUTO_PAUSED, 0.0)))
        assertEquals(listOf(AudioCue.AutoResumed), announcing.onState(state(RecordingStatus.RECORDING, 0.0)))
    }

    @Test
    fun `disabled cues say nothing and a restored planner does not repeat milestones`() {
        val off = AudioCuePlanner(AudioCueSettings(enabled = false), UnitSystem.METRIC)
        assertEquals(emptyList<AudioCue>(), off.onState(state(RecordingStatus.RECORDING, 5.0)))
        val restored = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC, state(RecordingStatus.RECORDING, 3.4, 20.minutes))
        assertEquals(emptyList<AudioCue>(), restored.onState(state(RecordingStatus.RECORDING, 3.9, 23.minutes)))
        assertEquals(1, restored.onState(state(RecordingStatus.RECORDING, 4.0, 24.minutes)).size)
    }

    @Test
    fun `notification formats`() {
        assertEquals("0:00", NotificationFormat.elapsed(Duration.ZERO))
        assertEquals("42:10", NotificationFormat.elapsed(42.minutes + 10.seconds))
        assertEquals("1:02:03", NotificationFormat.elapsed(62.minutes + 3.seconds))
        assertEquals("7.21", NotificationFormat.distance(7212.0, UnitSystem.METRIC, Locale.ROOT))
        assertEquals("1.00", NotificationFormat.distance(1609.344, UnitSystem.IMPERIAL, Locale.ROOT))
        assertEquals("5:00", NotificationFormat.pace(1000.0 / 300.0, UnitSystem.METRIC))
        assertNull(NotificationFormat.pace(0.0, UnitSystem.METRIC))
        assertNull(NotificationFormat.pace(0.1, UnitSystem.METRIC))
        assertEquals("36.0", NotificationFormat.speed(10.0, UnitSystem.METRIC, Locale.ROOT))
        assertTrue(NotificationFormat.showsPace(SportType.RUN))
        assertFalse(NotificationFormat.showsPace(SportType.RIDE))
    }

    @Test
    fun `wake lock is held only while the timer runs or auto-pause waits`() {
        assertTrue(WakeLockPolicy.shouldHold(RecordingStatus.RECORDING))
        assertTrue(WakeLockPolicy.shouldHold(RecordingStatus.AUTO_PAUSED))
        assertFalse(WakeLockPolicy.shouldHold(RecordingStatus.PAUSED))
        assertFalse(WakeLockPolicy.shouldHold(RecordingStatus.IDLE))
        assertFalse(WakeLockPolicy.shouldHold(RecordingStatus.FINISHED))
    }

    @Test
    fun `permission flow`() {
        val none = PermissionSnapshot(preciseLocation = false, notifications = false, canAskLocation = true, shouldAskNotifications = true)
        assertEquals(PermissionStep.LOCATION_RATIONALE, RecordingPermissionFlow.onStart(none))
        assertEquals(PermissionStep.LOCATION_SETTINGS, RecordingPermissionFlow.onStart(none.copy(canAskLocation = false)))
        val located = none.copy(preciseLocation = true)
        assertEquals(PermissionStep.NOTIFICATION_RATIONALE, RecordingPermissionFlow.onStart(located))
        assertNull(RecordingPermissionFlow.onStart(located.copy(notifications = true)))
        assertNull(RecordingPermissionFlow.onStart(located.copy(shouldAskNotifications = false)))
        // After the system dialog.
        assertEquals(PermissionStep.NOTIFICATION_RATIONALE, RecordingPermissionFlow.afterLocationResult(located))
        assertEquals(PermissionStep.LOCATION_SETTINGS, RecordingPermissionFlow.afterLocationResult(none.copy(canAskLocation = false)))
        assertEquals(PermissionStep.IDLE, RecordingPermissionFlow.afterLocationResult(none))
    }
}
