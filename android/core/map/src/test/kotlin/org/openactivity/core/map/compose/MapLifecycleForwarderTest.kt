// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class MapLifecycleForwarderTest {
    private val calls = mutableListOf<String>()
    private val forwarder = MapLifecycleForwarder(
        object : MapViewLifecycle {
            override fun onStart() { calls += "start" }
            override fun onResume() { calls += "resume" }
            override fun onPause() { calls += "pause" }
            override fun onStop() { calls += "stop" }
            override fun onDestroy() { calls += "destroy" }
        },
    )

    @Test
    fun jumpingToResumedCallsStartThenResume() {
        forwarder.moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf("start", "resume"), calls)
    }

    @Test
    fun backgroundingAndReturning() {
        forwarder.moveTo(Lifecycle.State.RESUMED)
        forwarder.moveTo(Lifecycle.State.CREATED)
        forwarder.moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf("start", "resume", "pause", "stop", "start", "resume"), calls)
    }

    @Test
    fun repeatedStatesAreIgnored() {
        forwarder.moveTo(Lifecycle.State.STARTED)
        forwarder.moveTo(Lifecycle.State.STARTED)
        assertEquals(listOf("start"), calls)
    }

    @Test
    fun leavingCompositionWhileResumedTearsDownInOrder() {
        forwarder.moveTo(Lifecycle.State.RESUMED)
        calls.clear()
        forwarder.destroy()
        assertEquals(listOf("pause", "stop", "destroy"), calls)
        assertEquals(Lifecycle.State.DESTROYED, forwarder.state)
    }

    @Test
    fun destroyIsIdempotentAndFinal() {
        forwarder.moveTo(Lifecycle.State.DESTROYED)
        forwarder.destroy()
        forwarder.moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf("destroy"), calls)
    }
}
