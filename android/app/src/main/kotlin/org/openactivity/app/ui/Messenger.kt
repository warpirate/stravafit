// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows a short transient message (snackbar) anchored above the bottom bar.
 * Screens use it for confirmations and recoverable errors.
 */
fun interface Messenger {
    fun show(message: String)
}

/** The app-wide [Messenger]; a no-op outside the app scaffold (previews, screenshot tests). */
val LocalMessenger = staticCompositionLocalOf { Messenger { } }
