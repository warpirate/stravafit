// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.PrivacyZoneError
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.data.repository.LocalPrivacyZoneRepository
import org.openactivity.core.data.repository.PrivacyZoneException
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.model.LatLng
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PrivacyZoneRepositoryTest {
    private lateinit var db: OpenActivityDatabase
    private lateinit var zones: LocalPrivacyZoneRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-03T06:00:00Z"), ZoneOffset.UTC)
    private val home = LatLng(18.5204, 73.8567)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        zones = LocalPrivacyZoneRepository(db.privacyZoneDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `add, edit and delete a zone`() = runTest {
        val id = zones.add("  Home  ", home, 400).getOrThrow()
        val added = zones.get(id)!!
        assertEquals("Home", added.name)
        assertEquals(home, added.center)
        assertEquals(400, added.radiusMeters)
        assertEquals(clock.instant(), added.createdAt)

        zones.update(id, "", LatLng(18.53, 73.86), 1600).getOrThrow()
        val edited = zones.observeZones().first().single()
        assertEquals("", edited.name)
        assertEquals(1600, edited.radiusMeters)
        assertEquals(LatLng(18.53, 73.86), edited.center)

        assertTrue(zones.delete(id))
        assertFalse(zones.delete(id))
        assertNull(zones.get(id))
        val missing = zones.update(id, "x", home, 200).exceptionOrNull() as PrivacyZoneException
        assertEquals(PrivacyZoneError.NOT_FOUND, missing.error)
    }

    @Test
    fun `radius snaps to an allowed choice`() = runTest {
        assertEquals(200, zones.get(zones.add("a", home, 50).getOrThrow())!!.radiusMeters)
        assertEquals(800, zones.get(zones.add("b", home, 700).getOrThrow())!!.radiusMeters)
        assertEquals(1600, zones.get(zones.add("c", home, 5000).getOrThrow())!!.radiusMeters)
    }

    @Test
    fun `no more than ten zones`() = runTest {
        repeat(PrivacyZones.MAX_ZONES) { zones.add("z$it", home, 200).getOrThrow() }
        val error = zones.add("one more", home, 200).exceptionOrNull() as PrivacyZoneException
        assertEquals(PrivacyZoneError.LIMIT_REACHED, error.error)
        assertEquals(PrivacyZones.MAX_ZONES, zones.zones().size)
    }

    @Test
    fun `long names are cut to the backend limit`() = runTest {
        val id = zones.add("x".repeat(80), home, 200).getOrThrow()
        assertEquals(PrivacyZones.MAX_NAME_LENGTH, zones.get(id)!!.name.length)
    }
}
