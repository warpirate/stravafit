// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.app.feature.notifications.InboxDestination
import org.openactivity.app.feature.notifications.InboxLink
import org.openactivity.app.feature.notifications.InboxLinkViewModel
import org.openactivity.app.testing.FakeNotificationRepository
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import java.time.LocalDate

class InboxKeysTest {
    private fun progress(target: Double, metric: GoalMetric = GoalMetric.DISTANCE) = GoalProgress(
        goal = Goal(id = 7, sport = null, period = GoalPeriod.WEEK, metric = metric, target = target),
        periodStart = LocalDate.of(2026, 10, 26),
        periodEnd = LocalDate.of(2026, 11, 2),
        current = target,
        elapsedFraction = 0.5,
        daysLeft = 3,
    )

    @Test
    fun `a goal raised mid-week can be reached and announced again`() {
        assertNotEquals(InboxText.goalReachedKey(progress(20_000.0)), InboxText.goalReachedKey(progress(40_000.0)))
        assertNotEquals(InboxText.goalReminderKey(progress(20_000.0)), InboxText.goalReminderKey(progress(40_000.0)))
        assertNotEquals(InboxText.goalReachedKey(progress(5.0, GoalMetric.COUNT)), InboxText.goalReachedKey(progress(5.0, GoalMetric.TIME)))
        // The same goal and target in the same week is one event.
        assertEquals(InboxText.goalReachedKey(progress(20_000.0)), InboxText.goalReachedKey(progress(20_000.0).copy(current = 25_000.0)))
    }

    @Test
    fun `opening a notification marks its entry read and leads to its destination`() = runTest {
        val inbox = FakeNotificationRepository()
        val id = inbox.add(NewNotification(NotificationType.GOAL_REACHED, "Goal reached", "", NotificationTarget.GOALS, "k"))!!
        val links = InboxLinkViewModel(inbox)
        assertEquals(InboxLink.To(InboxDestination.Goals), links.open(id))
        assertTrue(inbox.get(id)!!.isRead)
        assertEquals(InboxLink.Inbox, links.open(999))
    }
}
