// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import org.openactivity.app.feature.goals.GoalFormat
import org.openactivity.app.feature.me.ProgressFormat
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.EffortMedal
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeeklyStreak
import java.time.LocalDate

/**
 * The words of every on-device inbox entry, with the event key that keeps each one unique. Pure, so it is
 * unit-tested directly.
 */
internal object InboxText {
    /** Key of an activity's best-effort entry. */
    fun effortsKey(activityId: Long): String = "efforts:$activityId"

    // The metric and target are part of the key: a goal raised mid-period announces its new target too.
    fun goalReachedKey(progress: GoalProgress): String = "${goalKey(progress)}:reached"

    fun goalReminderKey(progress: GoalProgress): String = "${goalKey(progress)}:80"

    private fun goalKey(progress: GoalProgress): String = progress.goal.let {
        "goal:${it.id}:${it.metric}:${it.target.toLong()}:${progress.periodStart}"
    }

    fun weeklyKey(weekStart: LocalDate): String = "weekly:$weekStart"

    /** Key of a segment PR entry: one per segment and activity. */
    fun segmentPrKey(segmentId: Long, activityId: Long): String = "segment-pr:$segmentId:$activityId"

    /** "New segment PR: Riverside sprint in 1:42" with the activity and the time saved. */
    fun segmentPr(effort: SegmentEffort, previousBestSeconds: Long?): NewNotification {
        val time = UnitFormatter.duration(effort.elapsed.inWholeSeconds)
        val body = buildString {
            append(effort.activityTitle)
            if (previousBestSeconds != null && previousBestSeconds > effort.elapsed.inWholeSeconds) {
                append(" · ")
                append(UnitFormatter.duration(previousBestSeconds - effort.elapsed.inWholeSeconds))
                append(" faster than your old PR")
            }
        }
        return NewNotification(
            type = NotificationType.SEGMENT_PR,
            title = "New segment PR: ${effort.segmentName} in $time",
            body = body,
            target = NotificationTarget.ACTIVITY,
            dedupeKey = segmentPrKey(effort.segmentId, effort.activityId),
            activityId = effort.activityId,
        )
    }

    /**
     * "New PR: 5K in 24:12" (or "2nd best 10K: 51:40"), with every medal of the activity listed in the
     * body. Null when the activity earned no medal.
     */
    fun achievements(activity: SavedActivity, medals: List<EffortMedal>, formatter: UnitFormatter): NewNotification? {
        if (medals.isEmpty()) return null
        // The headline is the longest record, as on the feed card; otherwise the best place.
        val headline = medals.filter { it.isPersonalRecord }.maxByOrNull { it.target }
            ?: medals.minWith(compareBy<EffortMedal>({ it.isYearly }, { it.rank }, { -it.target }))
        val name = ProgressFormat.effortLabel(headline.target, formatter)
        val time = ProgressFormat.effortTime(headline.value)
        val title = if (headline.isPersonalRecord) "New PR: $name in $time" else "${medalWords(headline)} $name: $time"
        val others = medals.filter { it !== headline }
        val body = buildString {
            append(activity.title)
            if (others.isNotEmpty()) {
                append(" · ")
                append(others.joinToString(", ") { "${medalWords(it)} ${ProgressFormat.effortLabel(it.target, formatter)}" })
            }
        }
        val anyRecord = medals.any { it.isPersonalRecord }
        return NewNotification(
            type = if (anyRecord) NotificationType.PERSONAL_RECORD else NotificationType.BEST_EFFORT,
            title = title,
            body = body,
            target = NotificationTarget.ACTIVITY,
            dedupeKey = effortsKey(activity.id),
            activityId = activity.id,
        )
    }

    /** "PR", "2nd best", "Best of 2026", "3rd of 2026". */
    fun medalWords(medal: EffortMedal): String {
        val rank = medal.rank ?: return ""
        return when {
            !medal.isYearly -> if (rank == 1) "PR" else "${ordinal(rank)} best"
            rank == 1 -> "Best of ${medal.year}"
            else -> "${ordinal(rank)} of ${medal.year}"
        }
    }

    /** "Recording recovered": the app stopped mid-recording and the activity was saved from the journal. */
    fun recovered(activity: SavedActivity): NewNotification = NewNotification(
        type = NotificationType.RECORDING_RECOVERED,
        title = "Recording recovered",
        body = "${activity.title} was interrupted and saved from the recovered recording. Check its map and stats.",
        target = NotificationTarget.ACTIVITY,
        dedupeKey = "recovered:${activity.id}",
        activityId = activity.id,
    )

    /** "Weekly run goal reached": 32.4 / 30 km with 3 days to spare. */
    fun goalReached(progress: GoalProgress, formatter: UnitFormatter): NewNotification {
        val goal = progress.goal
        val spare = when (val days = progress.daysLeft - 1) {
            in Int.MIN_VALUE..0 -> "on the last day"
            1 -> "with a day to spare"
            else -> "with $days days to spare"
        }
        return NewNotification(
            type = NotificationType.GOAL_REACHED,
            title = "${GoalFormat.title(goal)} reached",
            body = "${GoalFormat.amount(goal.metric, progress.current, formatter)} ${GoalFormat.periodPhrase(goal.period)}, $spare.",
            target = NotificationTarget.GOALS,
            dedupeKey = goalReachedKey(progress),
        )
    }

    /** "80% of your weekly run goal": what is left and how long there is. */
    fun goalReminder(progress: GoalProgress, formatter: UnitFormatter): NewNotification {
        val goal = progress.goal
        return NewNotification(
            type = NotificationType.GOAL_REMINDER,
            title = "${GoalFormat.percent(progress)}% of your ${GoalFormat.title(goal).replaceFirstChar { it.lowercase() }}",
            body = "${GoalFormat.ofTarget(progress, formatter)}. ${GoalFormat.amount(goal.metric, progress.remaining, formatter)} to go, " +
                "${GoalFormat.daysLeft(progress).replaceFirstChar { it.lowercase() }}.",
            target = NotificationTarget.GOALS,
            dedupeKey = goalReminderKey(progress),
        )
    }

    /**
     * "Your week: 3 activities" with distance, time and climbing, and the streak when there is one.
     * Null for a week without activities (nothing to sum up, and no nagging).
     */
    fun weeklySummary(weekStart: LocalDate, totals: StatTotals, streak: WeeklyStreak, formatter: UnitFormatter): NewNotification? {
        if (totals.count == 0) return null
        val parts = buildList {
            if (totals.distanceMeters > 0) add(formatter.distance(totals.distanceMeters).let { "${it.value} ${it.unit}" })
            add(UnitFormatter.compactDuration(totals.movingTime.inWholeSeconds))
            if (totals.elevationGainMeters >= 1) add(formatter.elevation(totals.elevationGainMeters).let { "${it.value} ${it.unit} climbed" })
            if (streak.weeks > 1) add("${streak.weeks}-week streak")
        }
        val noun = if (totals.count == 1) "activity" else "activities"
        return NewNotification(
            type = NotificationType.WEEKLY_SUMMARY,
            title = "Your week: ${formatter.count(totals.count)} $noun",
            body = parts.joinToString(" · "),
            target = NotificationTarget.PROGRESS,
            dedupeKey = weeklyKey(weekStart),
        )
    }

    private fun ordinal(n: Int): String = when (n) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${n}th"
    }
}
