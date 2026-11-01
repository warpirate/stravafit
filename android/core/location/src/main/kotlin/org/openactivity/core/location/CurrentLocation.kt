// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.openactivity.model.LatLng
import javax.inject.Inject

/** Where the athlete is now, or why that is unknown. */
sealed interface CurrentLocationResult {
    data class Found(val position: LatLng) : CurrentLocationResult

    /** Location permission is missing or location is switched off. */
    data class Unavailable(val reason: LocationAvailability) : CurrentLocationResult

    /** Location is allowed but no fix arrived in time (indoors, for example). */
    data object NoFix : CurrentLocationResult
}

/** One-shot position for features that need "near me" rather than a stream of fixes. */
interface CurrentLocationProvider {
    /** Checks permission and settings without starting anything. */
    fun availability(): LocationAvailability

    /** The last known fix when recent enough, else one fresh fix (waiting at most ~20 s). */
    suspend fun current(): CurrentLocationResult
}

/**
 * [CurrentLocationProvider] over the platform's last known fixes (GPS, network, fused, passive), falling
 * back to the first fix from the flavour's [LocationSource]. Approximate location is enough here.
 */
class DeviceCurrentLocation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: LocationSource,
) : CurrentLocationProvider {

    override fun availability(): LocationAvailability = when {
        !LocationPermissions.hasAny(context) -> LocationAvailability.NO_PERMISSION
        else -> {
            val manager = context.getSystemService(LocationManager::class.java)
            if (manager == null || !androidx.core.location.LocationManagerCompat.isLocationEnabled(manager)) {
                LocationAvailability.DISABLED
            } else {
                LocationAvailability.AVAILABLE
            }
        }
    }

    override suspend fun current(): CurrentLocationResult {
        val availability = availability()
        if (availability != LocationAvailability.AVAILABLE) return CurrentLocationResult.Unavailable(availability)
        lastKnown()?.let { return CurrentLocationResult.Found(it) }
        // A fresh fix needs precise location from the recording source.
        if (source.availability() != LocationAvailability.AVAILABLE) return CurrentLocationResult.NoFix
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { source.locations().firstOrNull() }
        return fix?.let { CurrentLocationResult.Found(LatLng(it.latitude, it.longitude)) } ?: CurrentLocationResult.NoFix
    }

    @SuppressLint("MissingPermission") // availability() checked a location permission
    private fun lastKnown(): LatLng? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val now = SystemClock.elapsedRealtimeNanos()
        return try {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter { now - it.elapsedRealtimeNanos <= MAX_AGE_NANOS }
                .maxByOrNull { it.elapsedRealtimeNanos }
                ?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 20_000L
        const val MAX_AGE_NANOS = 10L * 60 * 1_000_000_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CurrentLocationModule {
    @Binds
    abstract fun bindCurrentLocation(impl: DeviceCurrentLocation): CurrentLocationProvider
}
