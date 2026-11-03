// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.openactivity.app.navigation.AppLinks
import org.openactivity.app.feature.stravaimport.StravaImportWorker
import org.openactivity.app.notifications.AndroidSystemNotifier
import org.openactivity.core.recordingservice.notification.RecordingNotifications

/**
 * Where a launch intent asks the app to go, beyond its normal start destination. The recording
 * notifications set extras (the ongoing and interrupted ones open the recorder, "Activity saved"
 * opens the saved activity; inbox notifications for records, goals and the weekly summary open
 * their entry's destination); other apps open GPX/TCX/FIT files with us or share them to us; links
 * (`openactivity://...`, our https host, the launcher shortcuts) name a screen.
 */
sealed interface LaunchRequest {
    /** Open the full-screen recorder. */
    data object OpenRecorder : LaunchRequest

    /** Open saved activity [id]. */
    data class OpenActivity(val id: Long) : LaunchRequest

    /** Files another app opened with us or shared to us: ask whether they are activities or a route. */
    data class ImportFiles(val uris: List<Uri>) : LaunchRequest

    /**
     * A link to a screen that arrived while the app was already running. On a fresh start the
     * navigation graph handles the launch intent's link itself, so [consume] reports links only when
     * asked to with `includeLinks`.
     */
    data class DeepLink(val uri: Uri) : LaunchRequest
    /** Open where inbox entry [id] leads, marking it read. */
    data class OpenInboxEntry(val id: Long) : LaunchRequest

    /** Open the inbox itself (the notifications' group summary). */
    data object OpenInbox : LaunchRequest

    /** Open the Strava import (its progress or result notification was tapped). */
    data object OpenStravaImport : LaunchRequest

    /** Health Connect asks why the app wants health data: show the privacy information. */
    data object ShowPrivacy : LaunchRequest

    companion object {
        /**
         * Reads and removes the request from [intent], so a configuration change or a re-delivered
         * intent does not navigate twice. Null when the intent asks for nothing, and when the app is
         * reopened from Recents: Android then replays the task's original intent, extras and data
         * included, which would otherwise reopen a notification's target or re-import a file long
         * after the tap.
         *
         * @param includeLinks report a [DeepLink] for an app link; false on a fresh start, where the
         *   navigation graph handles it.
         */
        fun consume(intent: Intent?, includeLinks: Boolean = false): LaunchRequest? {
            intent ?: return null
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
                intent.removeExtra(RecordingNotifications.EXTRA_OPEN_RECORDING)
                intent.removeExtra(RecordingNotifications.EXTRA_ACTIVITY_ID)
                intent.clearFiles()
                // A replayed link must not navigate again either.
                if (intent.data?.let(AppLinks::isAppLink) == true) intent.data = null
                intent.removeExtra(AndroidSystemNotifier.EXTRA_INBOX_ID)
                intent.removeExtra(AndroidSystemNotifier.EXTRA_OPEN_INBOX)
                intent.removeExtra(StravaImportWorker.EXTRA_OPEN_STRAVA_IMPORT)
                return null
            }
            if (intent.action in PRIVACY_ACTIONS) {
                intent.action = Intent.ACTION_MAIN
                return ShowPrivacy
            }
            if (intent.getBooleanExtra(StravaImportWorker.EXTRA_OPEN_STRAVA_IMPORT, false)) {
                intent.removeExtra(StravaImportWorker.EXTRA_OPEN_STRAVA_IMPORT)
                return OpenStravaImport
            }
            if (intent.getBooleanExtra(AndroidSystemNotifier.EXTRA_OPEN_INBOX, false)) {
                intent.removeExtra(AndroidSystemNotifier.EXTRA_OPEN_INBOX)
                return OpenInbox
            }
            if (intent.hasExtra(AndroidSystemNotifier.EXTRA_INBOX_ID)) {
                val id = intent.getLongExtra(AndroidSystemNotifier.EXTRA_INBOX_ID, NO_ID)
                intent.removeExtra(AndroidSystemNotifier.EXTRA_INBOX_ID)
                return if (id > 0) OpenInboxEntry(id) else null
            }
            if (intent.getBooleanExtra(RecordingNotifications.EXTRA_OPEN_RECORDING, false)) {
                intent.removeExtra(RecordingNotifications.EXTRA_OPEN_RECORDING)
                return OpenRecorder
            }
            if (intent.hasExtra(RecordingNotifications.EXTRA_ACTIVITY_ID)) {
                val id = intent.getLongExtra(RecordingNotifications.EXTRA_ACTIVITY_ID, NO_ID)
                intent.removeExtra(RecordingNotifications.EXTRA_ACTIVITY_ID)
                return if (id > 0) OpenActivity(id) else null
            }
            sharedFiles(intent).takeIf { it.isNotEmpty() }?.let { uris ->
                intent.clearFiles()
                return ImportFiles(uris)
            }
            val data = intent.data
            if (includeLinks && intent.action == Intent.ACTION_VIEW && data != null && AppLinks.isAppLink(data)) {
                return DeepLink(data)
            }
            return null
        }

        /** File URIs in a VIEW ("Open with") or SEND / SEND_MULTIPLE (share sheet) intent. */
        internal fun sharedFiles(intent: Intent): List<Uri> {
            val uris = when (intent.action) {
                Intent.ACTION_VIEW -> listOfNotNull(intent.data)
                Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
                Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra()
                else -> emptyList()
            }
            return uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE }.distinct()
        }

        private fun Intent.clearFiles() {
            if (action == Intent.ACTION_VIEW && data?.let { it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE } == true) {
                data = null
            }
            removeExtra(Intent.EXTRA_STREAM)
            if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) action = Intent.ACTION_MAIN
        }

        @Suppress("DEPRECATION")
        private fun Intent.streamExtra(): Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }

        @Suppress("DEPRECATION")
        private fun Intent.streamListExtra(): List<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }

        private const val NO_ID = -1L

        /**
         * Health Connect's "why does this app want my data" links: the rationale action on Android 13 and
         * lower, the permission-usage action (through an activity alias) on Android 14 and later.
         */
        internal val PRIVACY_ACTIONS = setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE")
    }
}
