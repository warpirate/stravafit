// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while [enabled] ("Screen display: always on", record detail 5.3). Uses the
 * view flag, so it only applies while the Record screen is on screen.
 */
@Composable
internal fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * While [enabled] (a recording is in progress), the Record screen shows over the lock screen when the phone
 * wakes, so the athlete can glance at the numbers or pause without unlocking. Other screens stay protected.
 */
@Composable
internal fun ShowOverLockScreen(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        activity.setShowOverLockScreen(enabled)
        onDispose { activity.setShowOverLockScreen(false) }
    }
}

private fun Activity.setShowOverLockScreen(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(show)
    } else {
        @Suppress("DEPRECATION")
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
