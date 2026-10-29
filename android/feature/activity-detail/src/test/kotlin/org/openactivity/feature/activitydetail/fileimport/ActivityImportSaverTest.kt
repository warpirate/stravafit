// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.fileimport.ActivityFileParser
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
}
