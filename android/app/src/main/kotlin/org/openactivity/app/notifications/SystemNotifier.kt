// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.app.MainActivity
import org.openactivity.app.R
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.datastore.NotificationSettings
import javax.inject.Inject
import javax.inject.Singleton

/** Android notification categories, one channel each (notifications-search A1). */
enum class NotificationCategory(val channelId: String) {
    ACHIEVEMENTS("achievements"),
    GOALS("goals"),
    WEEKLY_SUMMARY("weekly_summary"),
    ;

    /** Whether the athlete's in-app switch for this category is on. */
    fun enabledIn(settings: NotificationSettings): Boolean = when (this) {
        ACHIEVEMENTS -> settings.achievements
        GOALS -> settings.goals
        WEEKLY_SUMMARY -> settings.weeklySummary
    }

    companion object {
        /** The category an inbox entry of [type] posts under, or null when it stays in the inbox only. */
        fun of(type: NotificationType): NotificationCategory? = when (type) {
            NotificationType.PERSONAL_RECORD, NotificationType.BEST_EFFORT -> ACHIEVEMENTS
            NotificationType.GOAL_REACHED, NotificationType.GOAL_REMINDER -> GOALS
            NotificationType.WEEKLY_SUMMARY -> WEEKLY_SUMMARY
            NotificationType.IMPORT_FINISHED, NotificationType.IMPORT_FAILED, NotificationType.RECORDING_RECOVERED,
            NotificationType.BACKUP_FAILED,
            -> null
        }
    }
}

/** Posts Android notifications for inbox entries. */
interface SystemNotifier {
    /** Posts [entry] (stored in the inbox as [inboxId]) if its category is switched on and allowed. */
    fun post(inboxId: Long, entry: NewNotification, settings: NotificationSettings)
}

/**
 * [SystemNotifier] over [NotificationManagerCompat]. Nothing is posted while the POST_NOTIFICATIONS
 * permission is missing (Android 13+), while notifications are off for the app, or while the category's
 * in-app switch is off; the inbox keeps the entry either way. Tapping opens the entry's destination.
 */
@Singleton
class AndroidSystemNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemNotifier {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the channels (idempotent); called once at start-up. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                channel(NotificationCategory.ACHIEVEMENTS, R.string.channel_achievements_name, R.string.channel_achievements_description),
                channel(NotificationCategory.GOALS, R.string.channel_goals_name, R.string.channel_goals_description),
                channel(NotificationCategory.WEEKLY_SUMMARY, R.string.channel_weekly_name, R.string.channel_weekly_description),
            ),
        )
    }

    override fun post(inboxId: Long, entry: NewNotification, settings: NotificationSettings) {
        val category = NotificationCategory.of(entry.type) ?: return
        if (!category.enabledIn(settings) || !canPost()) return
        createChannels()
        val notification = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_inbox)
            .setContentTitle(entry.title)
            .setContentText(entry.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.body))
            .setContentIntent(openEntry(inboxId))
            .setColor(ACCENT)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setCategory(if (category == NotificationCategory.WEEKLY_SUMMARY) NotificationCompat.CATEGORY_RECOMMENDATION else NotificationCompat.CATEGORY_STATUS)
            .build()
        // Our own group summary: with two or more notifications Android collapses them under a summary, and
        // the one it makes up itself only launches the app. Ours opens the inbox.
        val summary = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_inbox)
            .setContentTitle(entry.title)
            .setColor(ACCENT)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(openInbox())
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(category.channelId, inboxId.toInt(), notification)
            manager.notify(SUMMARY_TAG, SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            // The permission went away between the check and the post; the inbox still has the entry.
        }
    }

    /** Notifications are allowed for the app (and, on Android 13+, the runtime permission is granted). */
    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun channel(category: NotificationCategory, name: Int, description: Int) =
        NotificationChannelCompat.Builder(category.channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(name))
            .setDescription(context.getString(description))
            .build()

    private fun openEntry(inboxId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        inboxId.toInt(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_INBOX_ID, inboxId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openInbox(): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_INBOX, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        /** Launch extra: the inbox entry whose notification was tapped. */
        const val EXTRA_INBOX_ID: String = "org.openactivity.app.extra.INBOX_ID"

        /** Launch extra: the group summary was tapped; open the inbox. */
        const val EXTRA_OPEN_INBOX: String = "org.openactivity.app.extra.OPEN_INBOX"

        private const val GROUP = "org.openactivity.app.INBOX"
        private const val SUMMARY_TAG = "inbox-summary"
        private const val SUMMARY_ID = 0

        /** StravaFit orange (#FC5200) for the small icon and the app name. */
        private const val ACCENT = 0xFFFC5200.toInt()
    }
}
