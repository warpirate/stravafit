// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.mapper.enumOrNull
import org.openactivity.core.data.mapper.sportOf
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.dao.GoalDao
import org.openactivity.core.database.entity.GoalEntity
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Training goals (weekly, monthly, yearly) and the weekly streak, all kept on the device. */
interface GoalRepository {
    /** Every goal, weekly first. */
    fun observeGoals(): Flow<List<Goal>>

    /** Each goal's progress in its current period, in the order of [observeGoals]. */
    fun observeProgress(): Flow<List<GoalProgress>>

    /** Consecutive weeks with at least one activity, ending this week. */
    fun observeStreak(): Flow<WeeklyStreak>

    suspend fun getGoal(id: Long): Goal?

    /** Adds [goal] (id 0) or replaces the goal with its id. Returns the goal's id. */
    suspend fun save(goal: Goal): Long

    /** Deletes a goal. Returns false when it did not exist. */
    suspend fun delete(id: Long): Boolean
}

/**
 * [GoalRepository] over Room. Weeks start on the athlete's chosen first day ([SettingsRepository]);
 * "now" comes from [clock], read each time the data changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalGoalRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val activityDao: ActivityDao,
    private val settingsRepository: SettingsRepository,
    @DataClock private val clock: Clock,
) : GoalRepository {

    private val firstDayOfWeek: Flow<DayOfWeek> = settingsRepository.settings.map { it.firstDayOfWeek }.distinctUntilChanged()

    override fun observeGoals(): Flow<List<Goal>> = goalDao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    // Both re-evaluate at every local midnight too: a screen left open into a new week, month or year
    // must not keep showing the old period. The query window follows the year it is evaluated in.
    override fun observeProgress(): Flow<List<GoalProgress>> = days().flatMapLatest { today ->
        // Margin before the year start covers a week that began in December and activities logged in
        // a time zone behind ours.
        val from = today.withDayOfYear(1).minusDays(MARGIN_DAYS).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        combine(observeGoals(), activityDao.observeStatRows(from), firstDayOfWeek) { goals, rows, firstDay ->
            val now = ZonedDateTime.now(clock)
            goals.map { GoalMath.progress(it, rows, now, firstDay) }
        }
    }

    override fun observeStreak(): Flow<WeeklyStreak> = combine(days(), firstDayOfWeek) { today, firstDay -> today to firstDay }
        .flatMapLatest { (today, firstDay) ->
            activityDao.observeStatRows(Long.MIN_VALUE).map { rows ->
                GoalMath.weeklyStreak(rows.map(StatsAggregator::localDate), today, firstDay)
            }
        }.distinctUntilChanged()

    /** Today, then each new day as local midnight passes. */
    private fun days(): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock)
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(MIN_TICK_MILLIS) + MIDNIGHT_SLACK_MILLIS)
            // A frozen clock (tests) never reaches midnight: stop instead of spinning on virtual time.
            if (clock.millis() == now.toInstant().toEpochMilli()) break
        }
    }.distinctUntilChanged()

    override suspend fun getGoal(id: Long): Goal? = goalDao.getById(id)?.toDomain()

    override suspend fun save(goal: Goal): Long {
        val now = clock.millis()
        if (goal.id != 0L) {
            val existing = goalDao.getById(goal.id)
            if (existing != null) {
                goalDao.update(goal.toEntity(createdAt = existing.createdAtMillis, updatedAt = now))
                return goal.id
            }
        }
        return goalDao.insert(goal.toEntity(createdAt = now, updatedAt = now).copy(id = 0))
    }

    override suspend fun delete(id: Long): Boolean = goalDao.delete(id) > 0

    private fun GoalEntity.toDomain(): Goal? {
        val period = enumOrNull<GoalPeriod>(period) ?: return null
        val metric = enumOrNull<GoalMetric>(metric)?.takeIf { it.allowedFor(period) } ?: return null
        if (!(target > 0.0 && target.isFinite())) return null
        // A sport this version does not know (written by a newer one) must not turn into "all sports".
        val sportType = sport?.let { sportOf(it) ?: return null }
        return Goal(id = id, sport = sportType, period = period, metric = metric, target = target)
    }

    private fun Goal.toEntity(createdAt: Long, updatedAt: Long) = GoalEntity(
        id = id,
        sport = sport?.name,
        period = period.name,
        metric = metric.name,
        target = target,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
    )

    private companion object {
        const val MARGIN_DAYS = 9L

        /** Waits a moment past midnight, so the clock reads the new day when it wakes. */
        const val MIDNIGHT_SLACK_MILLIS = 1_000L
        const val MIN_TICK_MILLIS = 1_000L
    }
}
