// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.NewNotification
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.core.data.repository.LocalGoalRepository
import org.openactivity.core.data.repository.LocalNotificationRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.NotificationSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class GoalAndInboxRepositoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    // Thursday 29 Oct 2026, 12:00 in Pune.
    private val clock = Clock.fixed(LocalDateTime.parse("2026-10-29T12:00").atZone(zone).toInstant(), zone)
    private lateinit var db: OpenActivityDatabase
    private val settings = MemorySettings()
    private lateinit var goals: LocalGoalRepository
    private lateinit var inbox: LocalNotificationRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        goals = LocalGoalRepository(db.goalDao(), db.activityDao(), settings, clock)
        inbox = LocalNotificationRepository(db.notificationDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun activity(uuid: String, at: String, sport: SportType = SportType.RUN, km: Double = 5.0): Long {
        val start = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli()
        return db.activityDao().insertActivity(
            ActivityEntity(
                uuid = uuid, sport = sport.name, title = uuid, description = "", visibility = "EVERYONE",
                perceivedExertion = null, startEpochMillis = start, timeZoneId = zone.id, elapsedMillis = 1_900_000,
                movingMillis = 1_800_000, distanceMeters = km * 1000, elevationGainMeters = 30.0,
                elevationLossMeters = 30.0, altitudeSource = "GPS", averageSpeedMps = 3.0, maxSpeedMps = 4.0,
                averageGradeAdjustedSpeedMps = null, averageHeartRate = null, maxHeartRate = null, averagePower = null,
                maxPower = null, averageCadence = null, autoPauseCount = 0, polyline = null, bboxSouth = null,
                bboxWest = null, bboxNorth = null, bboxEast = null, privacyHideStartMeters = 0.0,
                privacyHideEndMeters = 0.0, flags = "", routeId = null, createdAtMillis = start, updatedAtMillis = start,
            ),
        )
    }

    @Test
    fun `goals save, update and delete, and progress follows the activities`() = runTest {
        val id = goals.save(Goal(sport = SportType.RUN, period = GoalPeriod.WEEK, metric = GoalMetric.DISTANCE, target = 20_000.0))
        goals.save(Goal(sport = null, period = GoalPeriod.YEAR, metric = GoalMetric.TIME, target = 360_000.0))
        activity("a", "2026-10-26T07:00", km = 8.0)
        activity("b", "2026-10-28T07:00", sport = SportType.RIDE, km = 30.0)
        activity("c", "2026-10-20T07:00", km = 10.0)

        val progress = goals.observeProgress().first()
        assertEquals(listOf(GoalPeriod.WEEK, GoalPeriod.YEAR), progress.map { it.goal.period })
        assertEquals(8_000.0, progress[0].current, 0.0)
        assertEquals(3 * 1_800.0, progress[1].current, 0.0)

        assertEquals(id, goals.save(goals.getGoal(id)!!.copy(target = 5_000.0)))
        assertTrue(goals.observeProgress().first()[0].reached)
        assertTrue(goals.delete(id))
        assertFalse(goals.delete(id))
        assertNull(goals.getGoal(id))
        assertEquals(1, goals.observeGoals().first().size)
    }

    @Test
    fun `progress and streak respect the first day of the week`() = runTest {
        goals.save(Goal(sport = null, period = GoalPeriod.WEEK, metric = GoalMetric.COUNT, target = 3.0))
        activity("sun", "2026-10-25T07:00") // a Sunday
        activity("prev", "2026-10-21T07:00")
        assertEquals(0.0, goals.observeProgress().first().single().current, 0.0)
        assertEquals(WeeklyStreak(1, activeThisWeek = false), goals.observeStreak().first())

        settings.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        assertEquals(1.0, goals.observeProgress().first().single().current, 0.0)
        // Sunday-first: 25 Oct opens this week, 21 Oct is the week before.
        assertEquals(WeeklyStreak(2, activeThisWeek = true), goals.observeStreak().first())
    }

    @Test
    fun `the inbox records each event once and tracks unread entries`() = runTest {
        val first = NewNotification(NotificationType.WEEKLY_SUMMARY, "Your week", "3 activities", NotificationTarget.PROGRESS, "weekly:2026-10-19")
        val id = inbox.add(first)
        assertTrue(id != null)
        assertNull(inbox.add(first.copy(title = "again")))
        assertTrue(inbox.contains("weekly:2026-10-19"))
        val activityId = activity("pr", "2026-10-28T07:00")
        inbox.add(NewNotification(NotificationType.PERSONAL_RECORD, "New PR", "5K", NotificationTarget.ACTIVITY, "pr:1", activityId))
        assertEquals(2, inbox.observeUnreadCount().first())

        inbox.markRead(id!!)
        assertEquals(1, inbox.observeUnreadCount().first())
        val listed = inbox.observeNotifications().first()
        assertEquals(setOf("Your week", "New PR"), listed.map { it.title }.toSet())
        assertEquals(clock.instant(), listed.first().createdAt)
        assertEquals(NotificationTarget.ACTIVITY, listed.first { it.type == NotificationType.PERSONAL_RECORD }.target)

        inbox.markAllRead()
        assertEquals(0, inbox.observeUnreadCount().first())
        assertTrue(inbox.observeNotifications().first().all { it.isRead })
    }

    /** In-memory settings for the goal repository. */
    private class MemorySettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = state
        override suspend fun setUnits(units: UnitSystem) = state.update { it.copy(units = units) }
        override suspend fun setTheme(theme: ThemePreference) = state.update { it.copy(theme = theme) }
        override suspend fun setAutoPause(sport: SportType, enabled: Boolean) = Unit
        override suspend fun setAudioCues(cues: AudioCueSettings) = Unit
        override suspend fun setDefaultSport(sport: SportType) = Unit
        override suspend fun setDefaultVisibility(visibility: Visibility) = Unit
        override suspend fun setPrivacy(privacy: PrivacySettings) = Unit
        override suspend fun setFirstDayOfWeek(day: DayOfWeek) = state.update { it.copy(firstDayOfWeek = day) }
        override suspend fun setNotifications(notifications: NotificationSettings) = state.update { it.copy(notifications = notifications) }
        override suspend fun setSpeedOverride(sport: SportType, choice: org.openactivity.model.PaceOrSpeed?) = Unit
        override suspend fun setSwimPaceUnit(unit: org.openactivity.model.SwimPaceUnit?) = Unit
        override suspend fun setVisibilityOverride(sport: SportType, visibility: Visibility?) = Unit
        override suspend fun setLaunchTab(tab: org.openactivity.core.datastore.LaunchTab) = Unit
        override suspend fun setAutoLap(meters: Double) = Unit
        override suspend fun setOffRouteAlerts(enabled: Boolean) = Unit
    }
}
