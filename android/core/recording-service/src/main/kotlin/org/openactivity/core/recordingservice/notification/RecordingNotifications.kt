// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.recordingservice.R
import org.openactivity.core.recordingservice.RecordingService
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the recording notifications: the ongoing one with live time, distance and pace plus
 * Pause/Resume and Finish actions (record detail 4.5), and the follow-ups for saved and interrupted
 * recordings. Tapping any of them opens the app's launcher activity with [EXTRA_OPEN_RECORDING] or
 * [EXTRA_ACTIVITY_ID] set.
 */
@Singleton
class RecordingNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the channels (idempotent). */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RECORDING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.recording_channel_name))
                    .setDescription(context.getString(R.string.recording_channel_description))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_STATUS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.recording_status_channel_name))
                    .setDescription(context.getString(R.string.recording_status_channel_description))
                    .build(),
            ),
        )
    }

    /** The ongoing notification for [snapshot]. */
    fun ongoing(snapshot: RecordingUiSnapshot): Notification {
        val sportName = snapshot.sport?.let(DefaultTitles::sportName).orEmpty()
        val title = when (snapshot.status) {
            RecordingStatus.PAUSED -> context.getString(R.string.recording_title_paused, sportName)
            RecordingStatus.AUTO_PAUSED -> context.getString(R.string.recording_title_auto_paused, sportName)
            else -> context.getString(R.string.recording_title_recording, sportName)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(title)
            .setContentText(summary(snapshot))
            .setContentIntent(openApp(REQUEST_OPEN, EXTRA_OPEN_RECORDING, true))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (snapshot.status == RecordingStatus.PAUSED) {
            builder.addAction(0, context.getString(R.string.recording_action_resume), serviceAction(RecordingService.ACTION_RESUME))
        } else if (snapshot.status.isActive) {
            builder.addAction(0, context.getString(R.string.recording_action_pause), serviceAction(RecordingService.ACTION_PAUSE))
        }
        if (snapshot.status.isActive) {
            builder.addAction(0, context.getString(R.string.recording_action_finish), serviceAction(RecordingService.ACTION_FINISH))
        }
        return builder.build()
    }

    /** "42:10 · 7.21 km · 5:51 /km" (speed instead of pace for rides). */
    fun summary(snapshot: RecordingUiSnapshot): String {
        val units = snapshot.units
        val metric = units == UnitSystem.METRIC
        val parts = mutableListOf(
            NotificationFormat.elapsed(snapshot.elapsed),
            "${NotificationFormat.distance(snapshot.distanceMeters, units)} ${context.getString(if (metric) R.string.unit_km else R.string.unit_mi)}",
        )
        val speed = if (snapshot.status == RecordingStatus.RECORDING) snapshot.currentSpeedMps else snapshot.averageSpeedMps
        if (NotificationFormat.showsPace(snapshot.sport)) {
            NotificationFormat.pace(speed, units)?.let {
                parts += "$it ${context.getString(if (metric) R.string.pace_per_km else R.string.pace_per_mi)}"
            }
        } else {
            parts += "${NotificationFormat.speed(speed, units)} ${context.getString(if (metric) R.string.speed_kmh else R.string.speed_mph)}"
        }
        return parts.joinToString(" · ")
    }

    /** Posts "Activity saved", opening the activity when tapped. Silently skipped without permission. */
    fun showSaved(activityId: Long, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(context.getString(R.string.recording_saved_title))
            .setContentText(context.getString(R.string.recording_saved_text, title))
            .setContentIntent(openApp(REQUEST_SAVED, EXTRA_ACTIVITY_ID, activityId))
            .setAutoCancel(true)
            .build()
        notify(ID_STATUS, notification)
    }

    /** Posts "Recording stopped unexpectedly – tap to resume" (recording design §16.2). */
    fun showInterrupted() {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(context.getString(R.string.recording_interrupted_title))
            .setContentText(context.getString(R.string.recording_interrupted_text))
            .setContentIntent(openApp(REQUEST_OPEN, EXTRA_OPEN_RECORDING, true))
            .setAutoCancel(true)
            .build()
        notify(ID_STATUS, notification)
    }

    /** Re-posts the ongoing notification (the service owns it while in the foreground). */
    fun update(snapshot: RecordingUiSnapshot) = notify(ID_ONGOING, ongoing(snapshot))

    private fun notify(id: Int, notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked: the recording itself carries on.
        }
    }

    private fun serviceAction(action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(requestCode: Int, extra: String, value: Any): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (value) {
            is Long -> intent.putExtra(extra, value)
            is Boolean -> intent.putExtra(extra, value)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_RECORDING: String = "recording"
        const val CHANNEL_STATUS: String = "recording_status"
        const val ID_ONGOING: Int = 1001
        const val ID_STATUS: Int = 1002

        /** Boolean extra on the launch intent: open the Record screen. */
        const val EXTRA_OPEN_RECORDING: String = "org.openactivity.extra.OPEN_RECORDING"

        /** Long extra on the launch intent: open this saved activity. */
        const val EXTRA_ACTIVITY_ID: String = "org.openactivity.extra.ACTIVITY_ID"

        private const val REQUEST_OPEN = 1
        private const val REQUEST_SAVED = 2
    }
}
