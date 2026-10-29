// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Permission and settings checks shared by every location source. */
object LocationPermissions {
    /** Permissions the Record screen requests (fine plus coarse, as Android 12+ requires both). */
    val REQUIRED: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasPrecise(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasAny(context: Context): Boolean =
        hasPrecise(context) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Combines permission and the system location switch into one [LocationAvailability]. */
    fun availability(context: Context, requireGpsProvider: Boolean): LocationAvailability {
        if (!hasAny(context)) return LocationAvailability.NO_PERMISSION
        if (!hasPrecise(context)) return LocationAvailability.APPROXIMATE_ONLY
        val manager = context.getSystemService(LocationManager::class.java) ?: return LocationAvailability.DISABLED
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationAvailability.DISABLED
        if (requireGpsProvider && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationAvailability.DISABLED
        }
        return LocationAvailability.AVAILABLE
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
