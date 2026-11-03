// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The permission and availability state machine behind the Health Connect settings. */
class HealthConnectStatusTest {
    private val available = HealthConnectStatus(HealthConnectAvailability.AVAILABLE)

    @Test
    fun `missing or outdated health connect disables both switches`() {
        val states = listOf(HealthConnectAvailability.NOT_INSTALLED, HealthConnectAvailability.UPDATE_REQUIRED, HealthConnectAvailability.NOT_SUPPORTED)
        for (availability in states) {
            val status = HealthConnectStatus(availability, granted = HealthPermissions.forWriting + HealthPermissions.forReading(true))
            assertFalse(status.canWrite)
            assertFalse(status.canRead)
            assertEquals(SyncSwitchState.UNAVAILABLE, status.switchState(SyncDirection.WRITE, enabled = true))
            assertEquals(SyncSwitchState.UNAVAILABLE, status.switchState(SyncDirection.READ, enabled = false))
        }
    }

    @Test
    fun `a switch that is on but no longer allowed asks for permission again`() {
        assertEquals(SyncSwitchState.OFF, available.switchState(SyncDirection.WRITE, enabled = false))
        assertEquals(SyncSwitchState.NEEDS_PERMISSION, available.switchState(SyncDirection.WRITE, enabled = true))
        assertEquals(SyncSwitchState.NEEDS_PERMISSION, available.switchState(SyncDirection.READ, enabled = true))
        val granted = available.copy(granted = setOf(HealthPermissions.WRITE_EXERCISE))
        assertEquals(SyncSwitchState.ON, granted.switchState(SyncDirection.WRITE, enabled = true))
        assertEquals(SyncSwitchState.NEEDS_PERMISSION, granted.switchState(SyncDirection.READ, enabled = true))
    }

    @Test
    fun `missing permissions per direction`() {
        assertEquals(HealthPermissions.forWriting, available.missing(SyncDirection.WRITE))
        assertEquals(HealthPermissions.forReading(false), available.missing(SyncDirection.READ))
        assertTrue(HealthPermissions.READ_IN_BACKGROUND in available.copy(backgroundReadAvailable = true).missing(SyncDirection.READ))
        val all = available.copy(granted = HealthPermissions.forWriting)
        assertTrue(all.missing(SyncDirection.WRITE).isEmpty())
        assertTrue(all.leftOut(SyncDirection.WRITE).isEmpty())
    }

    @Test
    fun `outcome of a permission request`() {
        assertEquals(PermissionRequestOutcome.DENIED, available.outcomeOf(SyncDirection.WRITE, emptySet()))
        // Health Connect returns the extras without the essential one when the user unticks "exercise".
        assertEquals(PermissionRequestOutcome.DENIED, available.outcomeOf(SyncDirection.WRITE, HealthPermissions.WRITE_EXTRAS))
        assertEquals(PermissionRequestOutcome.PARTLY_GRANTED, available.outcomeOf(SyncDirection.WRITE, setOf(HealthPermissions.WRITE_EXERCISE)))
        assertEquals(PermissionRequestOutcome.GRANTED, available.outcomeOf(SyncDirection.WRITE, HealthPermissions.forWriting))
        assertEquals(PermissionRequestOutcome.GRANTED, available.outcomeOf(SyncDirection.READ, HealthPermissions.forReading(false)))
        val withBackground = available.copy(backgroundReadAvailable = true)
        assertEquals(PermissionRequestOutcome.PARTLY_GRANTED, withBackground.outcomeOf(SyncDirection.READ, HealthPermissions.forReading(false)))
    }

    @Test
    fun `parts left out are named`() {
        val noRoute = available.copy(granted = setOf(HealthPermissions.WRITE_EXERCISE, HealthPermissions.HEART_RATE_WRITE))
        assertEquals(listOf("map", "distance"), noRoute.leftOut(SyncDirection.WRITE))
        val readNoHr = available.copy(granted = setOf(HealthPermissions.READ_EXERCISE, HealthPermissions.DISTANCE_READ))
        assertEquals(listOf("heart rate"), readNoHr.leftOut(SyncDirection.READ))
    }

    @Test
    fun `background reading needs the feature and its permission`() {
        val read = available.copy(granted = setOf(HealthPermissions.READ_EXERCISE, HealthPermissions.READ_IN_BACKGROUND))
        assertFalse(read.canReadInBackground)
        assertTrue(read.copy(backgroundReadAvailable = true).canReadInBackground)
        assertFalse(read.copy(backgroundReadAvailable = true, granted = setOf(HealthPermissions.READ_EXERCISE)).canReadInBackground)
    }
}
