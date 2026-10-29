// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformLocationSourceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(LocationManager::class.java)

    @Before
    fun enableGps() {
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @Test
    fun `without permission the flow completes empty`() = runTest {
        val source = PlatformLocationSource(app)
        assertEquals(LocationAvailability.NO_PERMISSION, source.availability())
        assertTrue(source.locations().toList().isEmpty())
    }

    @Test
    fun `approximate-only permission is reported`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(LocationAvailability.APPROXIMATE_ONLY, PlatformLocationSource(app).availability())
    }

    @Test
    fun `gps switched off is reported`() {
        shadowOf(app).grantPermissions(*LocationPermissions.REQUIRED)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        assertEquals(LocationAvailability.DISABLED, PlatformLocationSource(app).availability())
    }

    @Test
    fun `fixes are delivered with their monotonic time`() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(app).grantPermissions(*LocationPermissions.REQUIRED)
        val source = PlatformLocationSource(app)
        assertEquals(LocationAvailability.AVAILABLE, source.availability())
        val received = mutableListOf<org.openactivity.recording.EngineInput.Location>()
        val job = launch { source.locations().collect { received += it } }

        val fix = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 47.37
            longitude = 8.54
            accuracy = 6f
            altitude = 410.0
            speed = 3.2f
            time = 1_700_000_000_000L
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        shadowOf(manager).simulateLocation(fix)
        shadowOf(Looper.getMainLooper()).idle()
        job.cancel()

        val input = received.single()
        assertEquals(fix.elapsedRealtimeNanos, input.monotonicNanos)
        assertEquals(1_700_000_000_000L, input.wallClockMillis)
        assertEquals(47.37, input.latitude, 1e-9)
        assertEquals(6.0, input.horizontalAccuracyMeters!!, 1e-6)
        assertEquals(410.0, input.altitudeMeters!!, 1e-9)
        assertEquals(3.2, input.speedMps!!, 1e-6)
        assertNull(input.bearingDegrees)
    }
}
