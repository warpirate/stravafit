// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.GoalMath
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.core.data.repository.NotificationRepository
import org.openactivity.core.data.repository.SettingsRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sums up the week that just ended: an inbox entry, plus an Android notification when the weekly-summary
 * switch is on. It runs on the first morning of the week (Monday unless the athlete starts weeks on
 * another day) and once more on the second day if the device was off; the entry's key makes sure each
 * week is summed up only once. A week without activities gets no summary.
 */
class WeeklySummaryPoster @Inject constructor(
    private val activities: ActivityRepository,
    private val goals: GoalRepository,
    private val inbox: NotificationRepository,
    private val settings: SettingsRepository,
    private val notifier: SystemNotifier,
    private val clock: Clock,
) {
    /** Posts last week's summary if it is due. Returns true when an entry was added. */
    suspend fun run(): Boolean {
        val appSettings = settings.settings.first()
        val today = LocalDate.now(clock)
        val thisWeek = GoalMath.weekStart(today, appSettings.firstDayOfWeek)
        if (ChronoUnit.DAYS.between(thisWeek, today) >= CATCH_UP_DAYS) return false
        val lastWeek = thisWeek.minusWeeks(1)
        if (inbox.contains(InboxText.weeklyKey(lastWeek))) return false
        val weeks = activities.observeWeeklyTotals(2, null, appSettings.firstDayOfWeek).first()
        val totals = weeks.firstOrNull { it.weekStart == lastWeek }?.totals ?: return false
        val streak = goals.observeStreak().first()
        val formatter = UnitFormatter(appSettings.units, Locale.getDefault())
        val entry = InboxText.weeklySummary(lastWeek, totals, streak, formatter) ?: return false
        val id = inbox.add(entry) ?: return false
        notifier.post(id, entry, appSettings.notifications)
        return true
    }

    private companion object {
        /** The summary may still go out on the week's second day. */
        const val CATCH_UP_DAYS = 2L
    }
}

/** Daily WorkManager job (around 08:00) that lets [WeeklySummaryPoster] decide whether a summary is due. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, WeeklySummaryEntryPoint::class.java).weeklySummaryPoster().run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }

    /** Reaches the Hilt graph from a worker WorkManager created itself. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WeeklySummaryEntryPoint {
        fun weeklySummaryPoster(): WeeklySummaryPoster
    }

    companion object {
        private const val WORK_NAME = "weekly-summary"
        private val RUN_AT: LocalTime = LocalTime.of(8, 0)

        /** Schedules the daily check, first at the next 08:00 local time; keeps an existing schedule. */
        fun schedule(context: Context, clock: Clock) {
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextRun(ZonedDateTime.now(clock)).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Time from [now] to the next 08:00 in its zone. */
        fun delayUntilNextRun(now: ZonedDateTime): Duration {
            var next = now.with(RUN_AT)
            if (!next.isAfter(now)) next = now.plusDays(1).with(RUN_AT)
            return Duration.between(now, next)
        }
    }
}
