// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.export

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.export.art.ArtUnits
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.feature.activitydetail.Recordings
import org.openactivity.feature.activitydetail.RepositoryBackedTest
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RepositoryExportActivityProviderTest : RepositoryBackedTest() {

    private fun provider() = RepositoryExportActivityProvider(activities, settings, zones)

    @Test
    fun `stored activities map to the export model`() = runTest {
        val finished = Recordings.hillyRun(seconds = 600)
        val id = activities.saveFinished(
            finished,
            NewActivity("rec-x", title = "Bridge loop", hideStartMeters = 120.0, hideEndMeters = 80.0),
            UnitSystem.METRIC,
        )
        val export = provider().load(id.toString())!!
        assertEquals(id.toString(), export.id)
        assertEquals("Bridge loop", export.title)
        assertEquals(finished.points.count { !it.paused }, export.points.size)
        assertEquals(Instant.ofEpochMilli(finished.points.first().epochMillis), export.points.first().time)
        assertTrue(export.hasRoute && export.hasElevation && export.hasHeartRate && export.hasTimes)
        assertEquals(finished.distanceMeters, export.stats.distanceMeters, 1e-6)
        assertEquals(finished.movingTime.inWholeSeconds, export.stats.movingTimeSeconds)
        assertNull(export.stats.calories)
        assertEquals(120.0, export.privacy.hideStartMeters, 0.0)
        assertEquals(80.0, export.privacy.hideEndMeters, 0.0)
    }

    @Test
    fun `privacy zones come with every exported activity`() = runTest {
        val id = activities.saveFinished(Recordings.hillyRun(seconds = 300), NewActivity("rec-z"), UnitSystem.METRIC)
        zones.add("Home", org.openactivity.model.LatLng(47.0, 8.0), 800).getOrThrow()
        val zone = provider().load(id.toString())!!.privacy.zones.single()
        assertEquals(47.0, zone.latitude, 0.0)
        assertEquals(8.0, zone.longitude, 0.0)
        assertEquals(800.0, zone.radiusMeters, 0.0)
    }

    @Test
    fun `unknown or malformed ids load nothing`() = runTest {
        assertNull(provider().load("42"))
        assertNull(provider().load("not-a-number"))
    }

    @Test
    fun `units follow the setting`() = runTest {
        assertEquals(ArtUnits.METRIC, provider().units())
        settings.setUnits(UnitSystem.IMPERIAL)
        assertEquals(ArtUnits.IMPERIAL, provider().units())
    }

    @Test
    fun `every data format can be written from a stored activity`() = runTest {
        val id = activities.saveFinished(Recordings.hillyRun(seconds = 300), NewActivity("rec-y"), UnitSystem.METRIC)
        val exporter = ActivityFileExporter(provider(), org.openactivity.feature.activitydetail.testExporter(context), org.openactivity.core.export.share.GallerySaver(context))
        val options = exporter.options(id)!!
        assertEquals(ExportFormat.DATA, options.map { it.format })
        assertTrue(options.all { it.unavailable == null })
        for (format in ExportFormat.DATA) {
            val file = exporter.export(id, format)
            assertTrue("$format written", file.file.length() > 0)
            assertTrue(file.displayName.endsWith(".${format.extension}"))
        }
    }
}
