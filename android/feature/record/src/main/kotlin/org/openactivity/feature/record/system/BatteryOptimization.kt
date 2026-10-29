// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the system may put the app to sleep while it records (record detail 1.4). Some phone makers'
 * battery savers stop foreground services with the screen off unless the app is exempt.
 */
interface BatteryOptimization {
    /** True when the app is exempt from battery optimisation, so recording keeps running with the screen off. */
    fun isExempt(): Boolean
}

/** [BatteryOptimization] backed by [PowerManager]. */
class SystemBatteryOptimization @Inject constructor(
    @ApplicationContext private val context: Context,
) : BatteryOptimization {
    override fun isExempt(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/**
 * The system dialog that asks to exempt this app from battery optimisation, or the battery optimisation
 * list when a phone does not offer that dialog. The app is distributed outside Google Play (ADR-014), so
 * the direct request is allowed; the permission is declared in this module's manifest.
 */
@SuppressLint("BatteryLife")
fun batteryExemptionIntent(context: Context): Intent {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    return if (direct.resolveActivity(context.packageManager) != null) {
        direct
    } else {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
