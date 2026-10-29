// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.openactivity.recording.EngineInput
import javax.inject.Inject

/** [LocationSource] on the fused location provider: one high-accuracy fix per second. */
class FusedLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun availability(): LocationAvailability = LocationPermissions.availability(context, false)

    @SuppressLint("MissingPermission") // checked by availability(); a race is caught below
    override fun locations(): Flow<EngineInput.Location> = callbackFlow {
        if (availability() != LocationAvailability.AVAILABLE) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toEngineInput()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { close() }
        } catch (e: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }.buffer(FIX_BUFFER)

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
        const val FIX_BUFFER = 64
    }
}
