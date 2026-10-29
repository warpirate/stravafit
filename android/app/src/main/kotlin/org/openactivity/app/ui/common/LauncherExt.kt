// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher

/**
 * Launches [input], or calls [onUnavailable] when no app can handle it. Some devices (Android Go,
 * TV, managed profiles) ship without the system file picker, and launching a document contract
 * there throws instead of returning a null result.
 */
fun <I> ActivityResultLauncher<I>.launchOrElse(input: I, onUnavailable: () -> Unit) {
    try {
        launch(input)
    } catch (e: ActivityNotFoundException) {
        onUnavailable()
    }
}

/** Snackbar text when the system file picker is missing. */
const val NO_FILE_PICKER_MESSAGE: String = "No file picker is available on this device"
