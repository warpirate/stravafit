// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.EffortRanking
import org.openactivity.core.data.repository.GoalMath
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.core.data.repository.NotificationRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.recording.ActivityFlag
import java.time.Clock
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the newest activity the inbox has looked at. */
interface InboxWatermark {
    suspend fun get(): Long?

    suspend fun set(activityId: Long)
}

/** [InboxWatermark] in the app's local preferences. */
class PreferencesInboxWatermark @Inject constructor(private val preferences: LocalPreferences) : InboxWatermark {
    override suspend fun get(): Long? = preferences.inboxWatermark.first()

    override suspend fun set(activityId: Long) = preferences.setInboxWatermark(activityId)
}

/**
 * Turns newly saved activities into inbox entries, whichever way they arrived (recorded, recovered or
 * imported): best-effort medals (PR, 2nd, 3rd, all-time or yearly), recovered recordings, and goals reached
 * or 80% there. Achievements and goals also post an Android notification when their category is on.
 *
 * It works from a watermark, the newest activity id already looked at, so an activity saved while the app
 * was being killed is still announced on the next start, and each event's dedupe key stops it being
 * announced twice. On the very first run the watermark is set without announcing anything, so updating
 * the app does not flood the inbox with old records.
 */
@Singleton
class ActivityEventProcessor @Inject constructor(
    private val activities: ActivityRepository,
    private val goals: GoalRepository,
    private val inbox: NotificationRepository,
    private val settings: SettingsRepository,
    private val watermark: InboxWatermark,
    private val notifier: SystemNotifier,
    private val clock: Clock,
) {
    /** Watches the activity list for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            activities.observeActivities()
                .map { list -> list.maxOfOrNull { it.id } ?: 0L }
                .distinctUntilChanged()
                .collect {
                    try {
                        process()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A failed pass is retried with the next change; the watermark has not moved.
                    }
                }
        }
    }

    /** Looks at every activity newer than the watermark, then moves the watermark past them. */
    suspend fun process() {
        val all = activities.observeActivities().first()
        val newest = all.maxOfOrNull { it.id } ?: 0L
        val mark = watermark.get()
        if (mark == null) {
            watermark.set(newest)
            return
        }
        val fresh = all.filter { it.id > mark }.sortedBy { it.id }
        if (fresh.isEmpty()) return
        val appSettings = settings.settings.first()
        val formatter = UnitFormatter(appSettings.units, Locale.getDefault())
        // A bulk import is summed up by the import entry instead of one entry per old activity.
        val announceEfforts = fresh.size <= MAX_ANNOUNCED_AT_ONCE
        for (activity in fresh) {
            if (ActivityFlag.RECOVERED_AFTER_INTERRUPTION in activity.flags) record(InboxText.recovered(activity), appSettings)
            if (announceEfforts && isRecent(activity)) announceEfforts(activity, formatter, appSettings)
        }
        // Old activities (an import of last year) are not news for this period's goals; a batch of recent
        // ones still updates the inbox, but only a normal recording or two buzzes the phone.
        checkGoals(fresh.filter(::isRecent), formatter, appSettings, post = announceEfforts)
        watermark.set(maxOf(mark, newest))
    }

    private suspend fun announceEfforts(activity: SavedActivity, formatter: UnitFormatter, appSettings: AppSettings) {
        val records = activities.observeEffortRecords(EffortRanking.poolOf(activity.sport), EffortKind.DISTANCE).first()
        val medals = EffortRanking.medalsOf(records, EffortKind.DISTANCE, activity.id)
        InboxText.achievements(activity, medals, formatter)?.let { record(it, appSettings) }
    }

    private suspend fun checkGoals(fresh: List<SavedActivity>, formatter: UnitFormatter, appSettings: AppSettings, post: Boolean) {
        if (fresh.isEmpty()) return
        for (progress in goals.observeProgress().first()) {
            if (fresh.none { counts(progress, it) }) continue
            when {
                progress.reached -> record(InboxText.goalReached(progress, formatter), appSettings, post)
                progress.fraction >= GoalMath.REMINDER_FRACTION && !inbox.contains(InboxText.goalReachedKey(progress)) ->
                    record(InboxText.goalReminder(progress, formatter), appSettings, post)
            }
        }
    }

    /** Whether [activity] adds to [progress] in its current period. */
    private fun counts(progress: GoalProgress, activity: SavedActivity): Boolean {
        val day = activity.startTime.atZone(activity.timeZone).toLocalDate()
        return day >= progress.periodStart && day < progress.periodEnd && GoalMath.counts(progress.goal.sport, activity.sport)
    }

    private fun isRecent(activity: SavedActivity): Boolean =
        Duration.between(activity.startTime, clock.instant()) <= RECENT

    /** Adds [entry] to the inbox and, with [post], to the system tray. */
    private suspend fun record(entry: NewNotification, appSettings: AppSettings, post: Boolean = true) {
        val id = inbox.add(entry) ?: return
        if (post) notifier.post(id, entry, appSettings.notifications)
    }

    internal companion object {
        /** More new activities than this at once is an import: no per-activity achievement entries. */
        const val MAX_ANNOUNCED_AT_ONCE = 3

        /** Older activities (an imported file from last year) are not announced as new records. */
        val RECENT: Duration = Duration.ofDays(14)
    }
}
