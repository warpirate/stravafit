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
import org.openactivity.core.recordingservice.permission.RecordingPermissionRequester
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.Split
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingEngine
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.route.RouteEvent
import org.openactivity.recording.segment.LiveSegmentEvent
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PureLogicTest {
    private fun state(status: RecordingStatus, km: Double, moving: Duration = Duration.ZERO): RecordingState =
        RecordingEngine(Corpus.load("S001").config).state.copy(status = status, distanceMeters = km * 1000, movingTime = moving)

    @Test
    fun `milestones fire once per interval with the interval pace`() {
        val planner = AudioCuePlanner(INTERVAL_ONLY, UnitSystem.METRIC, SportType.RUN)
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
        val planner = AudioCuePlanner(INTERVAL_ONLY.copy(distanceInterval = 0.5), UnitSystem.IMPERIAL, SportType.RUN)
        val cue = planner.onState(state(RecordingStatus.RECORDING, 0.81)).single() as AudioCue.Milestone
        assertEquals(804.672, cue.distanceMeters, 1e-6)
    }

    @Test
    fun `pause and auto-pause cues follow the settings`() {
        val planner = AudioCuePlanner(AudioCueSettings(announceAutoPause = false), UnitSystem.METRIC, SportType.RUN)
        planner.onState(state(RecordingStatus.RECORDING, 0.0))
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.AUTO_PAUSED, 0.0)))
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 0.0)))
        assertEquals(listOf(AudioCue.Paused), planner.onState(state(RecordingStatus.PAUSED, 0.0)))
        assertEquals(listOf(AudioCue.Resumed), planner.onState(state(RecordingStatus.RECORDING, 0.0)))

        val announcing = AudioCuePlanner(INTERVAL_ONLY, UnitSystem.METRIC, SportType.RUN)
        announcing.onState(state(RecordingStatus.RECORDING, 0.0))
        assertEquals(listOf(AudioCue.AutoPaused), announcing.onState(state(RecordingStatus.AUTO_PAUSED, 0.0)))
        assertEquals(listOf(AudioCue.AutoResumed), announcing.onState(state(RecordingStatus.RECORDING, 0.0)))
    }

    @Test
    fun `disabled cues say nothing and a restored planner does not repeat milestones`() {
        val off = AudioCuePlanner(AudioCueSettings(enabled = false, distanceCues = true), UnitSystem.METRIC, SportType.RUN)
        assertEquals(emptyList<AudioCue>(), off.onState(state(RecordingStatus.RECORDING, 5.0)))
        val restored = AudioCuePlanner(INTERVAL_ONLY, UnitSystem.METRIC, SportType.RUN, state(RecordingStatus.RECORDING, 3.4, 20.minutes))
        assertEquals(emptyList<AudioCue>(), restored.onState(state(RecordingStatus.RECORDING, 3.9, 23.minutes)))
        assertEquals(1, restored.onState(state(RecordingStatus.RECORDING, 4.0, 24.minutes)).size)
    }

    private fun split(index: Int, moving: Duration) = Split(index, Distance(1000.0), moving, moving, null, isPartial = false)

    private fun lap(index: Int, meters: Double, moving: Duration, trigger: LapTrigger) =
        Lap(index, Duration.ZERO, moving, moving, Distance(meters), Distance.ZERO, trigger)

    @Test
    fun `each completed split is announced once, and the 1 km interval does not repeat it`() {
        val planner = AudioCuePlanner(AudioCueSettings(distanceCues = true), UnitSystem.METRIC, SportType.RUN)
        planner.onState(state(RecordingStatus.RECORDING, 0.0))
        val first = planner.onState(state(RecordingStatus.RECORDING, 1.0, 5.minutes).copy(splits = listOf(split(0, 5.minutes))))
        assertEquals(listOf(AudioCue.Split(1, 5.minutes, 300.0)), first)
        val again = planner.onState(state(RecordingStatus.RECORDING, 1.2, 6.minutes).copy(splits = listOf(split(0, 5.minutes))))
        assertEquals(emptyList<AudioCue>(), again)
        val second = planner.onState(
            state(RecordingStatus.RECORDING, 2.0, 11.minutes).copy(splits = listOf(split(0, 5.minutes), split(1, 6.minutes))),
        ).single() as AudioCue.Split
        assertEquals(2, second.number)
        assertEquals(11.minutes, second.movingTime)
        assertEquals(360.0, second.paceSecondsPerUnit!!, 1e-9)
    }

    @Test
    fun `rides hear speed, and turning splits off silences them`() {
        val ride = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC, SportType.RIDE)
        val cue = ride.onState(state(RecordingStatus.RECORDING, 1.0, 2.minutes).copy(splits = listOf(split(0, 2.minutes)))).single()
        assertTrue((cue as AudioCue.Split).speed)
        val quiet = AudioCuePlanner(AudioCueSettings(announceSplits = false), UnitSystem.METRIC, SportType.RUN)
        assertEquals(emptyList<AudioCue>(), quiet.onState(state(RecordingStatus.RECORDING, 1.0, 5.minutes).copy(splits = listOf(split(0, 5.minutes)))))
    }

    @Test
    fun `laps are announced with their time and pace, auto laps marked as such`() {
        val planner = AudioCuePlanner(AudioCueSettings(announceSplits = false), UnitSystem.METRIC, SportType.RUN)
        planner.onState(state(RecordingStatus.RECORDING, 0.0))
        val cue = planner.onState(
            state(RecordingStatus.RECORDING, 0.4, 2.minutes).copy(laps = listOf(lap(0, 400.0, 2.minutes, LapTrigger.DISTANCE))),
        ).single() as AudioCue.Lap
        assertEquals(1, cue.number)
        assertTrue(cue.automatic)
        assertEquals(300.0, cue.paceSecondsPerUnit!!, 1e-9)
        val off = AudioCuePlanner(AudioCueSettings(announceLaps = false), UnitSystem.METRIC, SportType.RUN)
        assertEquals(
            emptyList<AudioCue>(),
            off.onState(state(RecordingStatus.RECORDING, 0.4, 2.minutes).copy(laps = listOf(lap(0, 400.0, 2.minutes, LapTrigger.MANUAL)))),
        )
    }

    @Test
    fun `the time interval reads out every n minutes of timer time`() {
        val planner = AudioCuePlanner(AudioCueSettings(timeIntervalMinutes = 5), UnitSystem.METRIC, SportType.RUN)
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 0.9, 4.minutes).copy(timerTime = 4.minutes)))
        val readout = planner.onState(state(RecordingStatus.RECORDING, 0.95, 5.minutes).copy(timerTime = 5.minutes)).single()
        assertEquals(5.minutes, (readout as AudioCue.TimeReadout).timerTime)
        assertEquals(emptyList<AudioCue>(), planner.onState(state(RecordingStatus.RECORDING, 1.0, 6.minutes).copy(timerTime = 6.minutes)))
        assertEquals(1, planner.onState(state(RecordingStatus.RECORDING, 1.9, 10.minutes).copy(timerTime = 10.minutes)).size)
    }

    @Test
    fun `segment cues follow their toggle and an abandoned attempt stays quiet`() {
        val events = listOf(
            LiveSegmentEvent.Approaching(1, "Hill", 180.0),
            LiveSegmentEvent.Started(1, "Hill", 125_400L),
            LiveSegmentEvent.Finished(1, "Hill", 119_600L, -5_800L, isNewPr = true),
            LiveSegmentEvent.Abandoned(2, "Bridge"),
        )
        val on = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC, SportType.RUN)
        assertEquals(
            listOf(AudioCue.SegmentAhead("Hill", 180.0), AudioCue.SegmentStarted("Hill", 125L), AudioCue.SegmentFinished("Hill", 120L, -6L, newPr = true)),
            on.onSegments(events),
        )
        assertEquals(emptyList<AudioCue>(), AudioCuePlanner(AudioCueSettings(announceSegments = false), UnitSystem.METRIC, SportType.RUN).onSegments(events))
        assertEquals(emptyList<AudioCue>(), AudioCuePlanner(AudioCueSettings(enabled = false), UnitSystem.METRIC, SportType.RUN).onSegments(events))
    }

    @Test
    fun `route cues follow their toggle and status cues theirs`() {
        val events = listOf(RouteEvent.OffRoute(80.0), RouteEvent.BackOnRoute, RouteEvent.Completed)
        val on = AudioCuePlanner(AudioCueSettings(), UnitSystem.METRIC, SportType.RUN)
        assertEquals(listOf(AudioCue.OffRoute(80.0), AudioCue.BackOnRoute, AudioCue.RouteCompleted), on.onRoute(events))
        assertEquals(emptyList<AudioCue>(), AudioCuePlanner(AudioCueSettings(announceRoute = false), UnitSystem.METRIC, SportType.RUN).onRoute(events))

        val noStatus = AudioCuePlanner(AudioCueSettings(announceStatus = false), UnitSystem.METRIC, SportType.RUN)
        assertFalse(noStatus.announcesStatus)
        noStatus.onState(state(RecordingStatus.RECORDING, 0.0))
        assertEquals(emptyList<AudioCue>(), noStatus.onState(state(RecordingStatus.PAUSED, 0.0)))
    }

    private companion object {
        /** Only the custom distance interval, as the milestone tests expect. */
        val INTERVAL_ONLY = AudioCueSettings(distanceCues = true, announceSplits = false)
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

    @Test
    fun `notification permission is offered once, across requesters`() {
        var persisted = false
        var ready = 0
        fun requester() = RecordingPermissionRequester(
            snapshot = {
                PermissionSnapshot(preciseLocation = true, notifications = false, canAskLocation = true, shouldAskNotifications = !persisted)
            },
            launchLocation = {},
            launchNotifications = {},
            openSettings = {},
            onReady = { ready++ },
            notificationsAskedBefore = persisted,
            onNotificationsAsked = { persisted = true },
        )
        val first = requester()
        first.request()
        assertEquals(PermissionStep.NOTIFICATION_RATIONALE, first.step)
        first.dismiss() // "Not now" still counts as asked and starts the recording
        assertTrue(persisted)
        assertEquals(1, ready)

        val second = requester() // e.g. the Record screen opened again later
        second.request()
        assertEquals(PermissionStep.IDLE, second.step)
        assertEquals(2, ready)
    }
}
