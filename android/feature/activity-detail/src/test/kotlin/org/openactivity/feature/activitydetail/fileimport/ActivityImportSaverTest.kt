// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.fileimport.ActivityFileParser
import org.openactivity.core.fileimport.ManualActivity
import org.openactivity.feature.activitydetail.Recordings
import org.openactivity.feature.activitydetail.RepositoryBackedTest
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ActivityImportSaverTest : RepositoryBackedTest() {

    private fun saver() = ActivityImportSaver(activities, settings)

    private fun parse(gpx: String) = ActivityFileParser().parse("track.gpx", gpx.byteInputStream()).single()

    @Test
    fun `an imported file is stored with defaults and the default title`() = runTest {
        settings.state.value = settings.state.value.copy(defaultVisibility = Visibility.ONLY_ME, privacy = PrivacySettings(150.0, 250.0))
        val outcome = saver().save(parse(Gpx.run()), ZoneOffset.UTC)
        val saved = outcome as SaveOutcome.Saved
        val activity = activities.observeActivity(saved.activityId).first()!!
        assertEquals("Morning Run", activity.title)
        assertEquals(SportType.RUN, activity.sport)
        assertEquals(Visibility.ONLY_ME, activity.visibility)
        assertEquals(150.0, activity.hideStartMeters, 0.0)
        assertEquals(250.0, activity.hideEndMeters, 0.0)
        val detail = activities.observeActivityDetail(saved.activityId).first()!!
        assertEquals(Gpx.SAMPLES, detail.points.size)
        assertTrue(detail.splits(UnitSystem.METRIC).isNotEmpty())
        assertTrue(detail.splits(UnitSystem.IMPERIAL).isNotEmpty())
    }

    @Test
    fun `importing the same file twice finds the first copy`() = runTest {
        val first = saver().save(parse(Gpx.run())) as SaveOutcome.Saved
        val second = saver().save(parse(Gpx.run()))
        assertEquals(SaveOutcome.Duplicate(first.activityId, first.title), second)
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `a recording already stored by the app is not imported again`() = runTest {
        val recorded = Recordings.hillyRun(seconds = 900)
        val id = activities.saveFinished(recorded, NewActivity("rec-1", title = "Lunch loop"), UnitSystem.METRIC)
        val gpx = Gpx.fromPoints(activities.observeActivityDetail(id).first()!!.points.map { Triple(it.latitude, it.longitude, it.epochMillis) })
        val outcome = saver().save(parse(gpx))
        assertEquals(SaveOutcome.Duplicate(id, "Lunch loop"), outcome)
    }

    @Test
    fun `a planned route without times is rejected`() = runTest {
        val outcome = saver().save(parse(Gpx.route()))
        assertTrue(outcome is SaveOutcome.Rejected)
        assertTrue(activities.observeActivities().first().isEmpty())
    }

    @Test
    fun `an external activity keeps its own name, description, sport and zone`() = runTest {
        val details = ExternalImport("strava:1001", "Sunrise tempo", "Easy loop,\nthen strides", SportType.TRAIL_RUN, ZoneOffset.ofHoursMinutes(5, 30))
        val saved = saver().saveExternal(parse(Gpx.run()), details, saver().existingStarts()) as SaveOutcome.Saved
        val activity = activities.observeActivity(saved.activityId).first()!!
        assertEquals("Sunrise tempo", activity.title)
        assertEquals("Easy loop,\nthen strides", activity.description)
        assertEquals(SportType.TRAIL_RUN, activity.sport)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), activity.timeZone)
        assertEquals("strava:1001", activity.uuid)
        // The same uuid again is a duplicate, whatever the start index says.
        val again = saver().saveExternal(parse(Gpx.run()), details, ExistingStarts(emptyList()))
        assertEquals(SaveOutcome.Duplicate(saved.activityId, "Sunrise tempo"), again)
    }

    @Test
    fun `an external activity starting within a minute of one of the same kind is a duplicate`() = runTest {
        val recorded = Recordings.hillyRun(seconds = 900)
        val id = activities.saveFinished(recorded, NewActivity("rec-1", title = "Lunch loop"), UnitSystem.METRIC)
        val start = activities.observeActivity(id).first()!!.startTime
        val existing = saver().existingStarts()
        val manual = ManualActivity(SportType.TRAIL_RUN, start.plusSeconds(45), elapsedSeconds = 900, distanceMeters = 2400.0)
        assertEquals(SaveOutcome.Duplicate(id, "Lunch loop"), saver().saveManual(manual, ExternalImport("strava:9", "Run"), existing))
        // Two minutes later, or a ride at the same time, is another workout.
        val later = saver().saveManual(manual.copy(startTime = start.plusSeconds(120)), ExternalImport("strava:10", null), existing)
        assertTrue(later is SaveOutcome.Saved)
        val ride = saver().saveManual(manual.copy(sport = SportType.RIDE, startTime = start.plusSeconds(5)), ExternalImport("strava:11", null), existing)
        assertTrue(ride is SaveOutcome.Saved)
        // What this import saved counts for the rest of it.
        val sameAsLater = saver().saveManual(manual.copy(startTime = start.plusSeconds(150)), ExternalImport("strava:12", null), existing)
        assertEquals(SaveOutcome.Duplicate((later as SaveOutcome.Saved).activityId, later.title), sameAsLater)
    }

    @Test
    fun `same workout rule`() {
        val t = java.time.Instant.parse("2026-10-03T01:15:00Z")
        assertTrue(ActivityImportSaver.sameWorkout(t, SportType.RUN, t.plusSeconds(60), SportType.RUN))
        assertTrue(ActivityImportSaver.sameWorkout(t, SportType.RUN, t.minusSeconds(30), SportType.TRAIL_RUN))
        assertTrue(ActivityImportSaver.sameWorkout(t, SportType.RIDE, t, SportType.GRAVEL_RIDE))
        assertFalse(ActivityImportSaver.sameWorkout(t, SportType.RUN, t.plusSeconds(61), SportType.RUN))
        assertFalse(ActivityImportSaver.sameWorkout(t, SportType.RUN, t, SportType.RIDE))
    }

    @Test
    fun `a manual entry is stored without a map`() = runTest {
        val start = java.time.Instant.parse("2026-10-07T12:30:00Z")
        val manual = ManualActivity(SportType.YOGA, start, elapsedSeconds = 3600, averageHeartRate = 96.0, maxHeartRate = 121)
        val saved = saver().saveManual(manual, ExternalImport("strava:1004", "Yoga", "Hatha", zone = ZoneOffset.UTC), saver().existingStarts()) as SaveOutcome.Saved
        val detail = activities.observeActivityDetail(saved.activityId).first()!!
        assertTrue(detail.points.isEmpty())
        assertEquals(start, detail.activity.startTime)
        assertEquals(3600L, detail.activity.elapsedTime.inWholeSeconds)
        assertEquals(3600L, detail.activity.movingTime.inWholeSeconds)
        assertEquals(96.0, detail.activity.averageHeartRate!!, 0.0)
        assertEquals(121, detail.activity.maxHeartRate)
        assertEquals(null, detail.activity.polyline)
    }

    @Test
    fun `a manual entry without a duration is rejected`() = runTest {
        val manual = ManualActivity(SportType.RUN, java.time.Instant.parse("2026-10-07T12:30:00Z"), elapsedSeconds = 0)
        assertTrue(saver().saveManual(manual, ExternalImport("strava:1", null), saver().existingStarts()) is SaveOutcome.Rejected)
    }
}
