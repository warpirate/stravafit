// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.openactivity.recording.EngineInput
import javax.inject.Inject

/**
 * [LocationSource] on the platform `LocationManager` GPS provider: no Google services involved, so it is
 * the foss flavour's source and the gms flavour's fallback.
 */
class PlatformLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {
    private val manager: LocationManager? = context.getSystemService(LocationManager::class.java)

    override fun availability(): LocationAvailability =
        if (manager == null) LocationAvailability.DISABLED else LocationPermissions.availability(context, true)

    @SuppressLint("MissingPermission") // checked by availability(); a race is caught below
    override fun locations(): Flow<EngineInput.Location> = callbackFlow {
        val lm = manager
        if (lm == null || availability() != LocationAvailability.AVAILABLE) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListenerCompat {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(location.toEngineInput())
            }

            override fun onProviderDisabled(provider: String) {
                close()
            }
        }
        val request = LocationRequestCompat.Builder(INTERVAL_MILLIS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            LocationManagerCompat.requestLocationUpdates(
                lm, LocationManager.GPS_PROVIDER, request, listener, Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            close()
            return@callbackFlow
        } catch (e: IllegalArgumentException) {
            close() // no GPS provider on this device
            return@callbackFlow
        }
        awaitClose { LocationManagerCompat.removeUpdates(lm, listener) }
    }.buffer(FIX_BUFFER)

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
        const val FIX_BUFFER = 64
    }
}
