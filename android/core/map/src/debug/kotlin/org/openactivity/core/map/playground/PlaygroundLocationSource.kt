// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.playground

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real fixes from the platform [LocationManager] (no Google services, so it behaves the same in
 * both flavours). Debug playground only; the app's recorder has its own location layer.
 *
 * Collect only after the location permission has been granted.
 */
internal class PlaygroundLocationSource(context: Context) {
    private val manager = context.applicationContext.getSystemService(LocationManager::class.java)

    /** The best provider available on this device, or null when location is switched off. */
    fun provider(): String? {
        val providers = manager?.getProviders(true).orEmpty()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers ->
                LocationManager.FUSED_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    /** Fixes about once a second; emits the last known fix first when there is one. */
    @SuppressLint("MissingPermission") // Callers collect only after the runtime permission is granted.
    fun fixes(): Flow<Location> = callbackFlow {
        val lm = manager ?: run {
            close()
            return@callbackFlow
        }
        val provider = provider() ?: run {
            close()
            return@callbackFlow
        }
        lm.getLastKnownLocation(provider)?.let { trySend(it) }
        val listener = LocationListener { trySend(it) }
        lm.requestLocationUpdates(provider, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
        awaitClose { lm.removeUpdates(listener) }
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
    }
}
