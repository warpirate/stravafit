// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.map.camera.MapCamera
import org.openactivity.core.map.camera.MapPadding
import org.openactivity.model.LatLng

class MapStateTest {
    @Test
    fun followAndStop() {
        val state = MapState()
        assertEquals(CameraTracking.Free, state.tracking)
        state.followUser()
        assertEquals(CameraTracking.Follow, state.tracking)
        state.stopFollowing()
        assertEquals(CameraTracking.Free, state.tracking)
    }

    @Test
    fun fitStopsFollowingAndQueuesACommand() = runTest {
        val state = MapState(initialTracking = CameraTracking.Follow)
        state.fitTo(listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), padding = MapPadding.all(10f), animate = false)
        assertEquals(CameraTracking.Free, state.tracking)
        val command = state.commands.first() as CameraCommand.FitBounds
        assertEquals(1.0, command.bounds.south, 0.0)
        assertEquals(2.0, command.bounds.east, 0.0)
        assertEquals(MapPadding.all(10f), command.padding)
        assertEquals(false, command.animate)
    }

    @Test
    fun fittingNothingIsANoOp() {
        val state = MapState(initialTracking = CameraTracking.Follow)
        state.fitTo(emptyList<LatLng>())
        assertEquals(CameraTracking.Follow, state.tracking)
    }

    @Test
    fun savesAndRestoresCameraAndTracking() {
        val state = MapState(MapCamera(LatLng(18.5, 73.8), 14.0, bearing = 30.0, tilt = 10.0), CameraTracking.Follow)
        val saved = with(MapState.Saver) { AlwaysSave.save(state) }!!
        val restored = MapState.Saver.restore(saved)!!
        assertEquals(state.camera, restored.camera)
        assertEquals(CameraTracking.Follow, restored.tracking)
    }

    @Test
    fun savesStateWithoutACamera() {
        val saved = with(MapState.Saver) { AlwaysSave.save(MapState()) }!!
        val restored = MapState.Saver.restore(saved)!!
        assertNull(restored.camera)
        assertTrue(restored.tracking == CameraTracking.Free)
    }

    private object AlwaysSave : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
