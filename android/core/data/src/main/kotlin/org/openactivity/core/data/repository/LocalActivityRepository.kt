// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.mapper.FinishedActivityMapper
import org.openactivity.core.data.mapper.sportOf
import org.openactivity.core.data.mapper.toDistanceEffort
import org.openactivity.core.data.mapper.toDomain
import org.openactivity.core.data.mapper.toDurationEffort
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityMetadataUpdate
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.entity.ActivityMetadataEntity
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.FinishedActivity
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** [ActivityRepository] over the Room database. */
@Singleton
class LocalActivityRepository @Inject constructor(
    private val dao: ActivityDao,
    @DataClock private val clock: Clock,
) : ActivityRepository {

    override fun observeActivities(): Flow<List<SavedActivity>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeActivity(id: Long): Flow<SavedActivity?> =
        dao.observeById(id).map { it?.toDomain() }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActivityDetail(id: Long): Flow<ActivityDetail?> =
        observeActivity(id).flatMapLatest { activity ->
            if (activity == null) return@flatMapLatest flowOf(null)
            combine(
                dao.observePoints(id),
                dao.observeLaps(id),
                dao.observeSplits(id),
                dao.observeBestEfforts(id),
            ) { points, laps, splits, efforts ->
                ActivityDetail(
                    activity = activity,
                    points = points.map { it.toDomain() },
                    laps = laps.map { it.toDomain() },
                    metricSplits = splits.filter { it.unitSystem == UnitSystem.METRIC.name }.map { it.toDomain() },
                    imperialSplits = splits.filter { it.unitSystem == UnitSystem.IMPERIAL.name }.map { it.toDomain() },
                    bestEfforts = efforts.filter { it.kind == EffortKind.DISTANCE.name }.map { it.toDistanceEffort() },
                    durationEfforts = efforts.filter { it.kind == EffortKind.DURATION.name }.map { it.toDurationEffort() },
                )
            }
        }

    override suspend fun saveFinished(finished: FinishedActivity, options: NewActivity, recordedUnits: UnitSystem): Long {
        dao.getByUuid(options.uuid)?.let { return it.id }
        val rows = FinishedActivityMapper.toRows(finished, options, recordedUnits, clock.millis())
        return dao.insertActivityWithChildren(rows.activity, rows.points, rows.laps, rows.splits, rows.bestEfforts)
    }

    override suspend fun findIdByUuid(uuid: String): Long? = dao.getByUuid(uuid)?.id

    override suspend fun updateMetadata(id: Long, update: ActivityMetadataUpdate): Boolean = dao.updateMetadata(
        ActivityMetadataEntity(
            id = id,
            sport = update.sport.name,
            title = update.title.trim(),
            description = update.description,
            visibility = update.visibility.name,
            perceivedExertion = update.perceivedExertion,
            updatedAtMillis = clock.millis(),
        ),
    ) > 0

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    override fun observeWeeklyTotals(
        weeks: Int,
        sports: Set<SportType>?,
        firstDayOfWeek: DayOfWeek,
    ): Flow<List<WeekTotals>> {
        require(weeks > 0) { "weeks must be positive" }
        // A week of margin covers activities whose own time zone differs from ours; the aggregator drops
        // anything outside the requested weeks.
        val from = ZonedDateTime.now(clock).minusWeeks(weeks.toLong()).minusDays(8).toInstant().toEpochMilli()
        return dao.observeStatRows(from).map { rows ->
            val filtered = if (sports == null) rows else rows.filter { sportOf(it.sport) in sports }
            StatsAggregator.weekly(filtered, weeks, LocalDate.now(clock), firstDayOfWeek)
        }
    }

    override fun observeYearTotals(year: Int): Flow<YearTotals> {
        val from = LocalDate.of(year, 1, 1).minusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return dao.observeStatRows(from).map { StatsAggregator.year(it, year) }
    }

    override fun observeAllTimeTotals(): Flow<Map<SportType, StatTotals>> =
        dao.observeStatRows(Long.MIN_VALUE).map(StatsAggregator::bySport)

    override fun observePersonalBests(sports: Set<SportType>, kind: EffortKind): Flow<List<PersonalBest>> =
        dao.observeBestEfforts(kind.name, sports.map { it.name }).map { rows ->
            // Rows arrive best-first within each target, so the first row per target is the record.
            rows.distinctBy { it.target }.map { row ->
                PersonalBest(
                    target = row.target,
                    value = row.value,
                    activityId = row.activityId,
                    activityTitle = row.title,
                    sport = sportOf(row.sport) ?: SportType.OTHER,
                    achievedAt = Instant.ofEpochMilli(row.startEpochMillis),
                )
            }
        }
}
