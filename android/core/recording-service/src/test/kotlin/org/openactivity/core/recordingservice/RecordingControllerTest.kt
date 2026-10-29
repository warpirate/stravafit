// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.recordingservice.audio.AudioCue
import org.openactivity.core.recordingservice.journal.RecordingJournal
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.recording.ActivityFlag
import org.openactivity.recording.RecordingStatus
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * End-to-end: a corpus track replayed through a fake [org.openactivity.core.location.LocationSource] into
 * the real controller, engine, journal, repository and Room database, in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecordingControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler get() = dispatcher.scheduler
    private lateinit var db: OpenActivityDatabase
    private lateinit var activities: LocalActivityRepository
    private val settings = InMemorySettingsRepository(
        AppSettings(units = UnitSystem.METRIC, defaultVisibility = Visibility.EVERYONE, privacy = PrivacySettings(200.0, 100.0)),
    )
    private val speaker = RecordingSpeaker()
    private val launcher = CountingLauncher()

    private val track = Corpus.load("S001") // 5.2 km run at 5:00/km
    private val base = track.inputs.first().monotonicNanos
    private val baseWall = 1_780_297_200_000L
    private val lastFixMillis = (track.fixes.last().monotonicNanos - base) / 1_000_000L
    private lateinit var location: ReplayLocationSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        activities = LocalActivityRepository(db.activityDao(), Clock.fixed(Instant.ofEpochMilli(baseWall), ZoneOffset.UTC))
        location = ReplayLocationSource(scheduler, track.fixes, base)
    }

    @After
    fun tearDown() = db.close()

    private fun controller(scope: CoroutineScope) = DefaultRecordingController(
        locationSource = location,
        pressureSource = NoBarometer,
        activities = activities,
        settings = settings,
        journal = RecordingJournal(db.recordingJournalDao()),
        clock = FakeClock(scheduler, base, baseWall),
        launcher = launcher,
        speaker = speaker,
        scope = scope,
    )

    @Test
    fun `a corpus run is recorded, journalled and saved with splits and best efforts`() = runTest(dispatcher) {
        val controller = controller(backgroundScope)
        assertEquals(RecordingStatus.IDLE, controller.state.value.status)

        controller.start(SportType.RUN)
        assertEquals(1, launcher.starts)
        assertEquals(SportType.RUN, settings.settings.value.defaultSport)

        advanceTimeBy(600_000)
        runCurrent()
        val live = controller.state.value
        assertEquals(RecordingStatus.RECORDING, live.status)
        assertEquals(GpsState.GOOD, live.gpsStatus.state)
        assertEquals(2000.0, live.distanceMeters, 40.0)
        assertTrue(live.track.size > 500)
        assertEquals((live.distanceMeters / 1000).toInt(), live.splits.size)
        assertEquals(live.distanceMeters - live.splits.sumOf { it.distance.meters }, live.currentSplit.distanceMeters, 1e-6)
        val journal = db.recordingJournalDao().get()
        assertNotNull("journal is flushed while recording", journal)
        assertTrue(journal!!.pointCount > live.track.size - 10)

        advanceTimeBy(lastFixMillis - 600_000 + 500)
        runCurrent()
        val id = controller.finish()!!
        runCurrent()

        assertEquals(RecordingStatus.IDLE, controller.state.value.status)
        assertNull("journal is cleared after saving", db.recordingJournalDao().get())
        val detail = activities.observeActivityDetail(id).first()!!
        val activity = detail.activity
        assertEquals(5195.9, activity.distanceMeters, 52.0) // golden ±1 %
        assertEquals(1558.8, activity.movingTime.inWholeMilliseconds / 1000.0, 16.0)
        assertEquals(Visibility.EVERYONE, activity.visibility)
        assertEquals(200.0, activity.hideStartMeters, 0.0)
        assertEquals(100.0, activity.hideEndMeters, 0.0)
        assertNotNull(activity.polyline)
        assertEquals(5, detail.splits(UnitSystem.METRIC).count { !it.isPartial })
        assertEquals(3, detail.splits(UnitSystem.IMPERIAL).count { !it.isPartial })
        assertEquals(300.0, detail.bestEfforts.single { it.targetMeters == 1000.0 }.elapsedSeconds, 3.0)
        assertEquals(1500.0, detail.bestEfforts.single { it.targetMeters == 5000.0 }.elapsedSeconds, 5.0)
        assertTrue(detail.points.size > 1500)
        assertEquals(baseWall, activity.startTime.toEpochMilli())

        val milestones = speaker.spoken.filterIsInstance<AudioCue.Milestone>()
        assertEquals(listOf(1000.0, 2000.0, 3000.0, 4000.0, 5000.0), milestones.map { it.distanceMeters })
        assertEquals(AudioCue.Started(SportType.RUN), speaker.spoken.first())
        assertEquals(AudioCue.Saved, speaker.spoken.last())
    }

    @Test
    fun `process death mid-run is recovered from the journal with a gap`() = runTest(dispatcher) {
        val firstLife = CoroutineScope(dispatcher + SupervisorJob())
        val first = controller(firstLife)
        first.start(SportType.RUN)
        advanceTimeBy(600_000)
        runCurrent()
        val beforeDeath = first.state.value.distanceMeters
        firstLife.cancel() // the process dies: no finish, nothing else written

        advanceTimeBy(60_000) // dead for a minute; fixes in this window are lost
        val second = controller(backgroundScope)
        runCurrent()
        val interrupted = second.interruptedRecording.value
        assertNotNull(interrupted)
        assertEquals(SportType.RUN, interrupted!!.sport)

        assertTrue(second.recoverInterrupted())
        runCurrent()
        assertNull(second.interruptedRecording.value)
        val restored = second.state.value
        assertEquals(RecordingStatus.RECORDING, restored.status)
        assertTrue(restored.recoveredAfterInterruption)
        assertTrue("restored distance comes from the last flush", restored.distanceMeters in beforeDeath - 30.0..beforeDeath)

        advanceTimeBy(lastFixMillis - 660_000 + 500)
        runCurrent()
        val id = second.finish()!!
        val detail = activities.observeActivityDetail(id).first()!!
        assertTrue(ActivityFlag.RECOVERED_AFTER_INTERRUPTION in detail.activity.flags)
        // About 200 m (a minute at 3.3 m/s) were run while the app was dead and are not counted.
        assertEquals(5195.9 - 200.0, detail.activity.distanceMeters, 60.0)
        assertTrue(detail.activity.elapsedTime.inWholeSeconds >= 1559)
        assertEquals(2, detail.points.map { it.segment }.distinct().size)
        assertNull(db.recordingJournalDao().get())
        assertEquals(2, launcher.starts)
    }

    @Test
    fun `pause, resume, lap and discard`() = runTest(dispatcher) {
        val controller = controller(backgroundScope)
        controller.start(SportType.RUN)
        advanceTimeBy(120_000)
        runCurrent()
        controller.lap()
        assertEquals(1, controller.state.value.laps.size)

        controller.pause()
        assertEquals(RecordingStatus.PAUSED, controller.state.value.status)
        val movingAtPause = controller.state.value.movingTime
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(RecordingStatus.PAUSED, controller.state.value.status)
        assertEquals(movingAtPause, controller.state.value.movingTime)

        controller.resume()
        assertEquals(RecordingStatus.RECORDING, controller.state.value.status)
        assertTrue(AudioCue.Paused in speaker.spoken && AudioCue.Resumed in speaker.spoken)

        controller.discard()
        runCurrent()
        assertEquals(RecordingStatus.IDLE, controller.state.value.status)
        assertNull(db.recordingJournalDao().get())
        assertTrue(activities.observeActivities().first().isEmpty())
        assertNull(controller.finish())
    }

    @Test
    fun `without location permission the timer still runs and the status says why`() = runTest(dispatcher) {
        location.availability = LocationAvailability.NO_PERMISSION
        val controller = controller(backgroundScope)
        controller.start(SportType.WALK)
        advanceTimeBy(30_000)
        runCurrent()
        val s = controller.state.value
        assertEquals(GpsState.NO_PERMISSION, s.gpsStatus.state)
        assertEquals(30L, s.elapsed.inWholeSeconds)
        assertEquals(0.0, s.distanceMeters, 0.0)

        val id = controller.finish()!!
        val saved = activities.observeActivity(id).first()!!
        assertEquals(SportType.WALK, saved.sport)
        assertNull(saved.polyline)
    }

    @Test
    fun `a second start while recording is refused`() = runTest(dispatcher) {
        val controller = controller(backgroundScope)
        controller.start(SportType.RUN)
        val error = runCatching { controller.start(SportType.RIDE) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        controller.discard()
    }

    @Test
    fun `an interrupted recording can be saved at once or discarded`() = runTest(dispatcher) {
        val life = CoroutineScope(dispatcher + SupervisorJob())
        controller(life).apply { start(SportType.RUN) }
        advanceTimeBy(300_000)
        runCurrent()
        life.cancel()

        val next = controller(backgroundScope)
        val id = next.finishInterrupted()
        assertNotNull(id)
        assertEquals(1000.0, activities.observeActivity(id!!).first()!!.distanceMeters, 60.0)
        assertFalse(next.recoverInterrupted())

        val life2 = CoroutineScope(dispatcher + SupervisorJob())
        controller(life2).apply { start(SportType.RUN) }
        advanceTimeBy(10_000)
        runCurrent()
        life2.cancel()
        val third = controller(backgroundScope)
        third.discardInterrupted()
        runCurrent()
        assertNull(third.interruptedRecording.value)
        assertFalse(third.recoverInterrupted())
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `ticks advance time between fixes`() = runTest(dispatcher) {
        location.availability = LocationAvailability.DISABLED
        val controller = controller(backgroundScope)
        controller.start(SportType.RUN)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5L, controller.state.value.elapsed.inWholeSeconds)
        assertEquals(GpsState.DISABLED, controller.state.value.gpsStatus.state)
        controller.discard()
    }
}
