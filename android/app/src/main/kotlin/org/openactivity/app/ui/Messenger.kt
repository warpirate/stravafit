// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shows a short transient message (snackbar) anchored above the bottom bar.
 * Screens use it for actions whose destination screens are not built yet.
 */
fun interface Messenger {
    fun show(message: String)
}

/** The app-wide [Messenger]; a no-op outside the app scaffold (previews, screenshot tests). */
val LocalMessenger = staticCompositionLocalOf { Messenger { } }

/** Message for entry points whose screens arrive in a later milestone. */
fun notInThisBuild(feature: String): String = "$feature isn't in this preview build yet"
