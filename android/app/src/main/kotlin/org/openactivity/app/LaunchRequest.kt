// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.Intent
import org.openactivity.core.recordingservice.notification.RecordingNotifications

/**
 * Where a launch intent asks the app to go, beyond its normal start destination. The recording
 * notifications set these extras: the ongoing and interrupted ones open the recorder, the
 * "Activity saved" one opens the saved activity.
 */
sealed interface LaunchRequest {
    /** Open the full-screen recorder. */
    data object OpenRecorder : LaunchRequest

    /** Open saved activity [id]. */
    data class OpenActivity(val id: Long) : LaunchRequest

    companion object {
        /**
         * Reads and removes the request extras from [intent], so a configuration change or a
         * re-delivered intent does not navigate twice. Null when the intent asks for nothing.
         */
        fun consume(intent: Intent?): LaunchRequest? {
            intent ?: return null
            if (intent.getBooleanExtra(RecordingNotifications.EXTRA_OPEN_RECORDING, false)) {
                intent.removeExtra(RecordingNotifications.EXTRA_OPEN_RECORDING)
                return OpenRecorder
            }
            if (intent.hasExtra(RecordingNotifications.EXTRA_ACTIVITY_ID)) {
                val id = intent.getLongExtra(RecordingNotifications.EXTRA_ACTIVITY_ID, NO_ID)
                intent.removeExtra(RecordingNotifications.EXTRA_ACTIVITY_ID)
                return if (id > 0) OpenActivity(id) else null
            }
            return null
        }

        private const val NO_ID = -1L
    }
}
