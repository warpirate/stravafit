// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.privacy

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.testing.FakeCurrentLocation
import org.openactivity.app.testing.FakePrivacyZoneRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.location.CurrentLocationResult
import org.openactivity.core.location.LocationAvailability
import org.openactivity.geo.PrivacyZone
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PrivacyZonesViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val zones = FakePrivacyZoneRepository()
    private val settings = FakeSettingsRepository()
    private val location = FakeCurrentLocation()

    private fun editor(id: Long = 0) = PrivacyZoneEditorViewModel(SavedStateHandle(mapOf("zoneId" to id)), zones, location, settings)

    @Test
    fun `a long-pressed point becomes a new zone`() = runTest(main.dispatcher) {
        val vm = editor()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isNew)
        assertFalse(vm.uiState.value.canSave)
        val point = LatLng(18.53, 73.85)
        vm.setCenter(point)
        vm.setName("Home")
        vm.setRadius(800)
        vm.save()
        advanceUntilIdle()
        assertEquals(PrivacyZoneEditorEffect.Done, vm.effects.first())
        val saved = zones.state.value.single()
        assertEquals("Home", saved.name)
        assertEquals(point, saved.center)
        assertEquals(800, saved.radiusMeters)
    }

    @Test
    fun `current location centres the zone, or says why it cannot`() = runTest(main.dispatcher) {
        val vm = editor()
        advanceUntilIdle()
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals((location.result as CurrentLocationResult.Found).position, vm.uiState.value.center)
        assertNull(vm.uiState.value.locationProblem)

        location.result = CurrentLocationResult.Unavailable(LocationAvailability.NO_PERMISSION)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(LocationProblem.NO_PERMISSION, vm.uiState.value.locationProblem)
        assertFalse(vm.hasLocationPermission())

        location.result = CurrentLocationResult.NoFix
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(LocationProblem.NO_FIX, vm.uiState.value.locationProblem)
    }

    @Test
    fun `an existing zone loads, saves and deletes`() = runTest(main.dispatcher) {
        zones.add("Office", LatLng(18.55, 73.78), 400)
        val vm = editor(1)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isNew)
        assertEquals("Office", vm.uiState.value.name)
        vm.setName("Work")
        vm.save()
        advanceUntilIdle()
        assertEquals("Work", zones.state.value.single().name)

        val again = editor(1)
        advanceUntilIdle()
        again.requestDelete()
        assertTrue(again.uiState.value.confirmDelete)
        again.confirmDelete()
        advanceUntilIdle()
        assertTrue(zones.state.value.isEmpty())
    }

    @Test
    fun `the eleventh zone is refused with a reason`() = runTest(main.dispatcher) {
        repeat(PrivacyZones.MAX_ZONES) { zones.add("z$it", LatLng(18.5, 73.8), 200) }
        val vm = editor()
        advanceUntilIdle()
        vm.setCenter(LatLng(18.6, 73.9))
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.message.orEmpty().startsWith("You have 10 privacy zones"))
        assertFalse(vm.uiState.value.saving)
    }

    @Test
    fun `the list allows adding until ten zones`() = runTest(main.dispatcher) {
        val list = PrivacyZonesViewModel(zones, settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(list.uiState.value.canAdd)
        repeat(PrivacyZones.MAX_ZONES) { zones.add("z$it", LatLng(18.5, 73.8), 200) }
        advanceUntilIdle()
        assertFalse(list.uiState.value.canAdd)
    }

    @Test
    fun `the drawn ring lies on the zone's edge`() {
        val center = LatLng(18.5204, 73.8567)
        val ring = PrivacyZoneGeometry.ring(center, 400.0)
        assertEquals(ring.first().latitude, ring.last().latitude, 1e-9)
        assertTrue(ring.all { kotlin.math.abs(Spherical.distance(center, it) - 400.0) < 0.5 })
        assertTrue(ring.all { PrivacyZone(center, 400.5).contains(it) })
    }

    @Test
    fun `radius labels follow the units`() {
        assertEquals(listOf("200 m", "400 m", "800 m", "1.6 km"), PrivacyZones.RADII_METERS.map { PrivacyZoneGeometry.radiusLabel(it, UnitSystem.METRIC) })
        assertEquals(listOf("⅛ mi", "¼ mi", "½ mi", "1 mi"), PrivacyZones.RADII_METERS.map { PrivacyZoneGeometry.radiusLabel(it, UnitSystem.IMPERIAL) })
    }
}
