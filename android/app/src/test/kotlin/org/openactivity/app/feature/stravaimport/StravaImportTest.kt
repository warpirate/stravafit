// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.openactivity.app.testing.FakeActivityExtrasRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.core.fileimport.ManualActivity
import org.openactivity.core.fileimport.strava.JournalEntry
import org.openactivity.core.fileimport.strava.RowOutcome
import org.openactivity.core.fileimport.strava.StravaActivityRow
import org.openactivity.feature.activitydetail.fileimport.ActivityImportSaver
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StravaImportTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val temp = TemporaryFolder()

    private class FakeRunner(var persistable: Boolean = true) : StravaImportRunner {
        val enqueued = ArrayList<Boolean>()
        var cancelled = 0

        override fun keepAccess(uri: Uri): Boolean = persistable

        override fun displayName(uri: Uri): String = uri.lastPathSegment ?: "export.zip"

        override fun enqueue(restart: Boolean) {
            enqueued += restart
        }

        override fun cancel() {
            cancelled++
        }
    }

    private fun store() = StravaImportStore(temp.root.resolve("strava-import"))

    private fun viewModel(store: StravaImportStore, runner: StravaImportRunner, shared: String? = null) =
        StravaImportViewModel(store, runner, TEST_CLOCK, SavedStateHandle(mapOf(StravaImportViewModel.ARG_SHARED_URI to shared)))

    @Test
    fun `choosing a zip starts the job in the background`() = runTest {
        val store = store()
        val runner = FakeRunner()
        val vm = viewModel(store, runner)
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(StravaImportUiState.Mode.INTRO, vm.uiState.value.mode)

        vm.choose(Uri.parse("content://downloads/export_9001.zip"))
        advanceUntilIdle()
        val job = store.job.value!!
        assertEquals("export_9001.zip", job.sourceName)
        assertFalse(job.needsCopy)
        assertEquals(StravaImportState.QUEUED, job.state)
        assertEquals(listOf(true), runner.enqueued)
        assertEquals(StravaImportUiState.Mode.RUNNING, vm.uiState.value.mode)
    }

    @Test
    fun `cancel stops the worker and continue picks up again`() = runTest {
        val store = store()
        val runner = FakeRunner()
        val vm = viewModel(store, runner)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.choose(Uri.parse("content://downloads/export.zip"))
        advanceUntilIdle()
        store.update { it.copy(state = StravaImportState.RUNNING, done = 312, total = 1_204) }

        vm.cancel()
        advanceUntilIdle()
        assertEquals(StravaImportState.CANCELLED, store.job.value!!.state)
        assertEquals(1, runner.cancelled)
        assertEquals(StravaImportUiState.Mode.CANCELLED, vm.uiState.value.mode)

        vm.resume()
        advanceUntilIdle()
        assertEquals(StravaImportState.QUEUED, store.job.value!!.state)
        // Progress is kept: the worker continues from the journal.
        assertEquals(312, store.job.value!!.done)
        assertEquals(listOf(true, true), runner.enqueued)
    }

    @Test
    fun `a shared zip waits for the user and is copied in first`() = runTest {
        val store = store()
        val runner = FakeRunner(persistable = false)
        val vm = viewModel(store, runner, shared = "content://com.whatsapp.provider/export.zip")
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(StravaImportUiState.Mode.READY, vm.uiState.value.mode)
        assertTrue(runner.enqueued.isEmpty())
        vm.startPending()
        advanceUntilIdle()
        assertTrue(store.job.value!!.needsCopy)
        assertNull(vm.uiState.value.pending)
        assertEquals(StravaImportUiState.Mode.RUNNING, vm.uiState.value.mode)
    }

    @Test
    fun `the job survives the process and an unfinished one is resumed`() = runTest {
        val dir = temp.root.resolve("strava-import")
        val before = StravaImportStore(dir)
        before.begin(StravaImportJob(sourceUri = "content://x/export.zip", sourceName = "export.zip"))
        before.update { it.copy(state = StravaImportState.RUNNING, done = 40, total = 90) }
        before.journal.record(JournalEntry("1", "Morning Run", RowOutcome.Imported(1)))

        // A new process: a new store reads the same files.
        val after = StravaImportStore(dir)
        assertEquals(40, after.job.value!!.done)
        assertEquals(1, after.journal.entries().size)
        val runner = FakeRunner()
        viewModel(after, runner)
        assertEquals(listOf(false), runner.enqueued)
    }

    @Test
    fun `starting a new import forgets the old journal`() = runTest {
        val store = store()
        store.begin(StravaImportJob(sourceUri = "content://x/a.zip", sourceName = "a.zip"))
        store.journal.record(JournalEntry("1", "Run", RowOutcome.Imported(1)))
        store.update { it.copy(state = StravaImportState.DONE) }
        val vm = viewModel(store, FakeRunner())
        backgroundScope.launch { vm.uiState.collect {} }
        assertEquals(StravaImportUiState.Mode.DONE, vm.uiState.value.mode)
        vm.choose(Uri.parse("content://x/b.zip"))
        advanceUntilIdle()
        assertEquals("b.zip", store.job.value!!.sourceName)
        assertTrue(store.journal.entries().isEmpty())
    }

    @Test
    fun `progress and summary lines`() {
        val job = StravaImportJob(sourceUri = "u", sourceName = "n", phase = StravaImportPhase.ACTIVITIES, done = 312, total = 1_204)
        java.util.Locale.setDefault(java.util.Locale.UK)
        assertEquals("312 of 1,204 activities", StravaImportText.progressLine(job))
        assertEquals("Reading the list of activities", StravaImportText.progressLine(job.copy(total = 0)))
        assertEquals("Copying the ZIP into StravaFit", StravaImportText.progressLine(job.copy(phase = StravaImportPhase.COPYING)))
        assertEquals(
            "1,180 imported · 20 already here · 4 couldn't be imported · 37 photos",
            StravaImportText.summary(job.copy(imported = 1_180, duplicates = 20, failed = 4, photos = 37)),
        )
        assertEquals("0 imported", StravaImportText.summary(job))
    }

    private fun row(id: String, type: String, sport: SportType) = StravaActivityRow(
        id = id, start = Instant.parse("2026-10-03T01:15:00Z"), name = "Morning Run", typeName = type, sport = sport,
        description = "Easy loop", fileName = "activities/$id.gpx", media = emptyList(), distanceMeters = null,
        elapsedSeconds = 600, movingSeconds = 590, elevationGainMeters = null, averageHeartRate = null, maxHeartRate = null,
    )

    @Test
    fun `the csv type wins unless strava's type is unknown`() {
        val activity = ImportedActivity("1.gpx", ImportFormat.GPX, null, SportType.RIDE, true, null, emptyList(), 0.0, null, null, "k", "h")
        assertEquals(SportType.TRAIL_RUN, DatabaseStravaImportSink.sportFor(row("1", "Trail Run", SportType.TRAIL_RUN), activity))
        assertEquals(SportType.WORKOUT, DatabaseStravaImportSink.sportFor(row("1", "Workout", SportType.WORKOUT), activity))
        assertEquals(SportType.RIDE, DatabaseStravaImportSink.sportFor(row("1", "Unicycle", SportType.WORKOUT), activity))
        assertEquals(SportType.WORKOUT, DatabaseStravaImportSink.sportFor(row("1", "Unicycle", SportType.WORKOUT), activity.copy(sportDeclared = false)))
    }

    private lateinit var db: OpenActivityDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun `the sink stores rows as strava activities and finds them again`() = runTest {
        val activities = LocalActivityRepository(db.activityDao(), TEST_CLOCK)
        val saver = ActivityImportSaver(activities, FakeSettingsRepository())
        val sink = DatabaseStravaImportSink(saver, FakeActivityExtrasRepository(), saver.existingStarts(), temp.newFolder("photos"))
        val start = Instant.parse("2026-10-03T01:15:00Z")
        val points = List(20) { i -> ImportedPoint(18.5368 + i * 5e-5, 73.8791, 558.0, start.plusSeconds(i * 10L), heartRate = 140) }
        val track = ImportedActivity("1001.fit", ImportFormat.FIT, null, SportType.RUN, true, start, points, 110.0, 190, null, "k", "h", utcOffsetSeconds = 19_800)

        val outcome = sink.saveTrack(row("1001", "Trail Run", SportType.TRAIL_RUN), track) as RowOutcome.Imported
        val saved = activities.observeActivity(outcome.activityId).first()!!
        assertEquals("strava:1001", saved.uuid)
        assertEquals("Morning Run", saved.title)
        assertEquals("Easy loop", saved.description)
        assertEquals(SportType.TRAIL_RUN, saved.sport)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), saved.timeZone)

        // The same row again (a resumed or repeated import) is a duplicate, not a second copy.
        assertEquals(RowOutcome.Duplicate(outcome.activityId), sink.saveTrack(row("1001", "Trail Run", SportType.TRAIL_RUN), track))

        val manual = ManualActivity(SportType.YOGA, Instant.parse("2026-10-07T12:30:00Z"), 3_600)
        val yoga = sink.saveManual(row("1004", "Yoga", SportType.YOGA).copy(name = "Yoga"), manual) as RowOutcome.Imported
        assertEquals("Yoga", activities.observeActivity(yoga.activityId).first()!!.title)
        assertEquals(2, activities.observeActivities().first().size)
    }
}
