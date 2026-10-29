// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import android.app.Application
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.audio.AudioCue
import org.openactivity.core.recordingservice.audio.CuePhrases
import org.openactivity.core.recordingservice.journal.RecordingJournal
import org.openactivity.core.recordingservice.notification.RecordingNotifications
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingEngine
import org.openactivity.recording.RecordingStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPartsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `journal stores points incrementally and loads the exact snapshot`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(app, OpenActivityDatabase::class.java).allowMainThreadQueries().build()
        val journal = RecordingJournal(db.recordingJournalDao())
        val track = Corpus.load("S028") // laps, pause and heart rate
        val inputs = track.withTicks()
        val engine = RecordingEngine(track.config)
        engine.processAll(inputs.subList(0, 10))
        journal.begin("rec", SportType.RUN, 7L, engine.snapshot(), 1L, 2L)
        var flushedPoints = 0
        for (chunk in inputs.subList(10, inputs.size * 2 / 3).chunked(500)) {
            engine.processAll(chunk)
            journal.flush("rec", SportType.RUN, 7L, 2L, engine.snapshot(), 3L, 4L)
            flushedPoints = engine.snapshot().points.size
        }
        assertEquals(flushedPoints, db.recordingJournalDao().get()!!.pointCount)

        val loaded = RecordingJournal(db.recordingJournalDao()).load()!!
        assertEquals(engine.snapshot(), loaded.snapshot)
        assertEquals(7L, loaded.routeId)
        assertEquals(SportType.RUN, journal.header.first()!!.sport)

        journal.clear()
        assertNull(journal.load())
        db.close()
    }

    @Test
    fun `a journal that cannot be decoded is dropped`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(app, OpenActivityDatabase::class.java).allowMainThreadQueries().build()
        val engine = RecordingEngine(Corpus.load("S001").config)
        val journal = RecordingJournal(db.recordingJournalDao())
        journal.begin("rec", SportType.RUN, null, engine.snapshot(), 1L, 2L)
        val row = db.recordingJournalDao().get()!!
        db.recordingJournalDao().upsertJournal(row.copy(engineSnapshot = byteArrayOf(0, 1, 2)))
        assertNull(journal.load())
        assertNull(db.recordingJournalDao().get())
        db.close()
    }

    @Test
    fun `ongoing notification shows time, distance, pace and the right actions`() {
        val notifications = RecordingNotifications(app)
        notifications.createChannels()
        val running = RecordingUiSnapshot.idle().copy(
            status = RecordingStatus.RECORDING,
            sport = SportType.RUN,
            elapsed = 42.minutes + 10.seconds,
            distanceMeters = 7210.0,
            currentSpeedMps = 1000.0 / 351.0,
            gpsStatus = GpsStatus.forAccuracy(5.0),
        )
        val n = notifications.ongoing(running)
        assertEquals("Run · Recording", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("42:10 · ${String.format(Locale.getDefault(), "%.2f", 7.21)} km · 5:51 /km", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(listOf("Pause", "Finish"), n.actions.map { it.title.toString() })
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, n.category)

        val paused = notifications.ongoing(running.copy(status = RecordingStatus.PAUSED, averageSpeedMps = 2.0))
        assertEquals("Run · Paused", paused.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("Resume", "Finish"), paused.actions.map { it.title.toString() })

        val ride = notifications.summary(running.copy(sport = SportType.RIDE, currentSpeedMps = 10.0, units = UnitSystem.IMPERIAL))
        assertTrue(ride, ride.endsWith("mph"))
    }

    @Test
    fun `cue phrases`() {
        val phrases = CuePhrases(app.resources, Locale.US)
        assertEquals("Run started", phrases.phrase(AudioCue.Started(SportType.RUN), UnitSystem.METRIC))
        assertEquals(
            "Distance 2 kilometres. Time 10 minutes 32 seconds. Split pace 5 minutes 16 seconds per kilometre.",
            phrases.phrase(AudioCue.Milestone(2000.0, 10.minutes + 32.seconds, 316.0), UnitSystem.METRIC),
        )
        assertEquals("1 mile", phrases.distance(1609.344, UnitSystem.IMPERIAL))
        assertEquals("1.5 miles", phrases.distance(1609.344 * 1.5, UnitSystem.IMPERIAL))
        assertEquals("1 hour 1 second", phrases.duration(3601))
        assertEquals("0 seconds", phrases.duration(0))
        assertEquals("Auto-paused", phrases.phrase(AudioCue.AutoPaused, UnitSystem.METRIC))
    }
}
