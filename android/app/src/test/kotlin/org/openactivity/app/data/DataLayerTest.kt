// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.app.data.export.GpxZipWriter
import org.openactivity.app.data.local.FileProfilePhotoStore
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.data.route.toRouteInput
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.InMemoryPreferencesDataStore
import org.openactivity.app.testing.PUNE_LOOP
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.core.export.file.TrackFileEncoder
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DataLayerTest {

    @Test
    fun `preferences adapter reads and writes the shared settings`() = runTest {
        val settings = FakeSettingsRepository()
        val repo = SettingsBackedPreferencesRepository(settings, LocalPreferences(InMemoryPreferencesDataStore()))
        repo.setUnitSystem(UnitSystem.IMPERIAL)
        repo.setThemeMode(ThemeMode.Dark)
        repo.setLastSport(Sport.GravelRide)
        repo.setReduceMotion(true)
        val prefs = repo.preferences.first()
        assertEquals(UnitSystem.IMPERIAL, prefs.unitSystem)
        assertEquals(ThemeMode.Dark, prefs.themeMode)
        assertEquals(Sport.GravelRide, prefs.lastSport)
        assertTrue(prefs.reduceMotion)
        assertEquals(ThemePreference.DARK, settings.state.value.theme)
        assertEquals(SportType.GRAVEL_RIDE, settings.state.value.defaultSport)
    }

    @Test
    fun `local profile round trips and trims`() = runTest {
        val local = LocalPreferences(InMemoryPreferencesDataStore())
        assertEquals("You", local.profile.first().displayName)
        local.setProfileDetails("  Asha ", " Pune ")
        local.setPhotoPath("/files/profile/a.jpg")
        val profile = local.profile.first()
        assertEquals("Asha", profile.name)
        assertEquals("Pune", profile.location)
        assertEquals("/files/profile/a.jpg", profile.photoPath)
        local.setPhotoPath(null)
        assertNull(local.profile.first().photoPath)
    }

    @Test
    fun `zip export writes one GPX per GPS activity and skips indoor ones`() = runTest {
        val run = savedActivity(1, "Morning Run")
        val run2 = savedActivity(2, "Morning Run")
        val yoga = savedActivity(3, "Yoga", sport = SportType.YOGA, distanceMeters = 0.0, track = null)
        val repo = FakeActivityRepository(listOf(run, run2, yoga))
        repo.details.value = mapOf(
            1L to detail(run, withTrack = true),
            2L to detail(run2, withTrack = true),
            3L to detail(yoga, withTrack = false),
        )
        val out = ByteArrayOutputStream()
        val summary = GpxZipWriter(repo, TrackFileEncoder()).write(out)
        assertEquals(2, summary.exported)
        assertEquals(1, summary.skipped)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                assertTrue(String(zip.readBytes()).contains("<trkpt"))
            }
        }
        assertEquals(listOf("2026-10-27-Morning_Run.gpx", "2026-10-27-Morning_Run-2.gpx"), names)
    }

    @Test
    fun `imported files become routes named after the track or the file`() {
        val points = PUNE_LOOP.map { ImportedPoint(it.latitude, it.longitude, elevation = 550.0) } + ImportedPoint(null, null)
        val named = imported(points, title = "Sunday loop", sport = SportType.RIDE, declared = true).toRouteInput()!!
        assertEquals("Sunday loop", named.name)
        assertEquals(PUNE_LOOP.size, named.points.size)
        assertEquals(SportType.RIDE, named.sport)
        val unnamed = imported(points, title = null, sport = SportType.OTHER, declared = false).toRouteInput()!!
        assertEquals("loop-export", unnamed.name)
        assertNull(unnamed.sport)
        assertNull(imported(points.take(1), title = "x", sport = SportType.RUN, declared = true).toRouteInput())
    }

    @Test
    fun `photo sample size keeps the short side above the target`() {
        assertEquals(1, FileProfilePhotoStore.sampleSize(800, 600, 512))
        assertEquals(4, FileProfilePhotoStore.sampleSize(4000, 3000, 512))
    }

    @Test
    fun `photo orientation turns camera portraits upright`() {
        // EXIF orientation values 1..8 (ExifInterface.ORIENTATION_*).
        assertEquals(0f to false, FileProfilePhotoStore.orientationTransform(1))
        assertEquals(0f to true, FileProfilePhotoStore.orientationTransform(2))
        assertEquals(180f to false, FileProfilePhotoStore.orientationTransform(3))
        assertEquals(180f to true, FileProfilePhotoStore.orientationTransform(4))
        assertEquals(270f to true, FileProfilePhotoStore.orientationTransform(5))
        assertEquals(90f to false, FileProfilePhotoStore.orientationTransform(6))
        assertEquals(90f to true, FileProfilePhotoStore.orientationTransform(7))
        assertEquals(270f to false, FileProfilePhotoStore.orientationTransform(8))
        assertEquals(0f to false, FileProfilePhotoStore.orientationTransform(0))
    }

    private fun imported(points: List<ImportedPoint>, title: String?, sport: SportType, declared: Boolean) = ImportedActivity(
        sourceName = "loop-export.gpx",
        format = ImportFormat.GPX,
        title = title,
        sport = sport,
        sportDeclared = declared,
        startTime = null,
        points = points,
        distanceMeters = 1_000.0,
        elapsedSeconds = null,
        deviceName = null,
        dedupeKey = "k",
        contentHash = "h",
    )

    private fun detail(activity: org.openactivity.core.data.model.SavedActivity, withTrack: Boolean): ActivityDetail {
        val start = TEST_CLOCK.millis() - 3_600_000
        val points = if (!withTrack) emptyList() else PUNE_LOOP.mapIndexed { i, p ->
            ActivityPoint(p.latitude, p.longitude, 550.0, start + i * 10_000L, i * 10_000L, 140, null, null, 3.0, 5.0, i * 30.0, paused = false, segment = 0)
        }
        return ActivityDetail(activity, points, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
