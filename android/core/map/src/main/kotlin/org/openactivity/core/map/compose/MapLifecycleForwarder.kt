// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.lifecycle.Lifecycle
import org.maplibre.android.maps.MapView

/** The lifecycle callbacks a MapLibre `MapView` needs after `onCreate`. */
internal interface MapViewLifecycle {
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onDestroy()
}

internal fun MapView.asMapViewLifecycle(): MapViewLifecycle = object : MapViewLifecycle {
    override fun onStart() = this@asMapViewLifecycle.onStart()
    override fun onResume() = this@asMapViewLifecycle.onResume()
    override fun onPause() = this@asMapViewLifecycle.onPause()
    override fun onStop() = this@asMapViewLifecycle.onStop()
    override fun onDestroy() = this@asMapViewLifecycle.onDestroy()
}

/**
 * Walks a map view through lifecycle states one step at a time, so it never sees `onResume`
 * without `onStart` or `onDestroy` while still started, whatever order the host delivers events in
 * and even when the composable leaves composition while the screen is resumed.
 *
 * The view is assumed to have had `onCreate` already (state [Lifecycle.State.CREATED]).
 */
internal class MapLifecycleForwarder(private val target: MapViewLifecycle) {
    var state: Lifecycle.State = Lifecycle.State.CREATED
        private set

    /** Moves to [newState], calling every intermediate callback. [Lifecycle.State.DESTROYED] destroys. */
    fun moveTo(newState: Lifecycle.State) {
        if (state == Lifecycle.State.DESTROYED) return
        if (newState == Lifecycle.State.DESTROYED || newState == Lifecycle.State.INITIALIZED) {
            destroy()
            return
        }
        while (state < newState) {
            when (state) {
                Lifecycle.State.CREATED -> { target.onStart(); state = Lifecycle.State.STARTED }
                Lifecycle.State.STARTED -> { target.onResume(); state = Lifecycle.State.RESUMED }
                else -> return
            }
        }
        while (state > newState) {
            when (state) {
                Lifecycle.State.RESUMED -> { target.onPause(); state = Lifecycle.State.STARTED }
                Lifecycle.State.STARTED -> { target.onStop(); state = Lifecycle.State.CREATED }
                else -> return
            }
        }
    }

    /** Steps down to CREATED and destroys the view. Safe to call more than once. */
    fun destroy() {
        if (state == Lifecycle.State.DESTROYED) return
        moveTo(Lifecycle.State.CREATED)
        target.onDestroy()
        state = Lifecycle.State.DESTROYED
    }
}
