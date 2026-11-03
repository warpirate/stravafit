// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.mapper.sportOf
import org.openactivity.core.data.mapper.toDomain
import org.openactivity.core.data.mapper.zoneOf
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.data.model.Segment
import org.openactivity.core.data.model.SegmentDraft
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.data.model.SegmentRules
import org.openactivity.core.data.model.SegmentSummary
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.dao.SegmentDao
import org.openactivity.core.database.entity.ActivityMatchRow
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.SegmentEffortEntity
import org.openactivity.core.database.entity.SegmentEffortRow
import org.openactivity.core.database.entity.SegmentEntity
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.polyline.DouglasPeucker
import org.openactivity.polyline.PolylineCodec
import org.openactivity.recording.segment.LiveSegment
import org.openactivity.recording.segment.SegmentMatch
import org.openactivity.recording.segment.SegmentMatchConfig
import org.openactivity.recording.segment.SegmentMatcher
import org.openactivity.recording.segment.SegmentShape
import org.openactivity.recording.segment.SegmentTrack
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

/** Personal segments, their efforts and the matcher that finds them (segments design §2-§7, parity A4). */
interface SegmentRepository {
    /** Every segment with effort count and best time; starred first, then newest. */
    fun observeSegments(): Flow<List<SegmentSummary>>

    /** One segment, or null once it is deleted. */
    fun observeSegment(id: Long): Flow<Segment?>

    /** A segment's efforts, newest first. */
    fun observeEffortsOfSegment(segmentId: Long): Flow<List<SegmentEffort>>

    /** An activity's segment efforts, in the order they happened. */
    fun observeEffortsOfActivity(activityId: Long): Flow<List<SegmentEffort>>

    /** Your standing personal record on each segment that has efforts, by segment name. */
    fun observePersonalRecords(): Flow<List<SegmentEffort>>

    /**
     * Saves points [startIndex]..[endIndex] of activity [activityId] as a new, starred segment and returns
     * its id. Efforts are not matched here: run [rematchSegment] (in the background) afterwards.
     *
     * @throws IllegalArgumentException when the range is invalid or shorter than [SegmentRules.MIN_LENGTH_METERS].
     */
    suspend fun create(activityId: Long, startIndex: Int, endIndex: Int, name: String, sport: SportType): Long

    /** Returns false when the segment does not exist or [name] is blank. */
    suspend fun rename(id: Long, name: String): Boolean

    suspend fun setStarred(id: Long, starred: Boolean)

    /** Deletes a segment and its efforts. Returns false when it did not exist. */
    suspend fun delete(id: Long): Boolean

    /**
     * Matches activity [activityId] against every segment of its sport family and stores the efforts with
     * frozen places. Segments it was matched on before are skipped, so calling it twice is harmless.
     *
     * @return the efforts added.
     */
    suspend fun matchActivity(activityId: Long): List<SegmentEffort>

    /**
     * Matches segment [segmentId] against every stored activity again, oldest first, replacing its efforts;
     * places are frozen as if each activity had been saved in date order. Returns the number of efforts.
     */
    suspend fun rematchSegment(segmentId: Long): Int

    /** Starred segments of [sport]'s family with your PR on each, for live segments while recording. */
    suspend fun liveSegments(sport: SportType): List<LiveSegment>
}

/** Measures a range of an activity as a segment would be (shared by the create screen and [SegmentRepository.create]). */
object SegmentMath {
    /** Climbs smaller than this between two kept samples are GPS/barometer noise. */
    private const val CLIMB_NOISE_METERS = 1.0

    /** The draft for points [startIndex]..[endIndex] (clamped and ordered). */
    fun draft(points: List<ActivityPoint>, startIndex: Int, endIndex: Int): SegmentDraft {
        if (points.isEmpty()) return SegmentDraft(0, 0, emptyList(), 0.0, 0.0, 0.0, hasElevation = false)
        val s = minOf(startIndex, endIndex).coerceIn(0, points.lastIndex)
        val e = maxOf(startIndex, endIndex).coerceIn(0, points.lastIndex)
        val range = points.subList(s, e + 1)
        val distance = (points[e].distanceMeters - points[s].distanceMeters).coerceAtLeast(0.0)
        val elevations = range.mapNotNull { it.elevationMeters }
        val hasElevation = elevations.size >= 2 && range.first().elevationMeters != null && range.last().elevationMeters != null
        return SegmentDraft(
            startIndex = s,
            endIndex = e,
            points = range.map { LatLng(it.latitude, it.longitude) },
            distanceMeters = distance,
            elevationGainMeters = if (hasElevation) gain(elevations) else 0.0,
            averageGradePercent = if (hasElevation && distance > 0.0) {
                (range.last().elevationMeters!! - range.first().elevationMeters!!) / distance * 100.0
            } else {
                0.0
            },
            hasElevation = hasElevation,
        )
    }

    /** Total climb, ignoring wiggles below [CLIMB_NOISE_METERS]. */
    fun gain(elevations: List<Double>): Double {
        if (elevations.isEmpty()) return 0.0
        var total = 0.0
        var anchor = elevations.first()
        for (h in elevations) {
            when {
                h - anchor >= CLIMB_NOISE_METERS -> {
                    total += h - anchor
                    anchor = h
                }
                h < anchor -> anchor = h
            }
        }
        return total
    }
}

/** [SegmentRepository] over Room. */
@Singleton
class LocalSegmentRepository @Inject constructor(
    private val dao: SegmentDao,
    private val activityDao: ActivityDao,
    @DataClock private val clock: Clock,
) : SegmentRepository {
    private val config = SegmentMatchConfig()

    override fun observeSegments(): Flow<List<SegmentSummary>> = dao.observeSummaries().map { rows ->
        rows.mapNotNull { row ->
            val segment = row.segment.toDomain() ?: return@mapNotNull null
            SegmentSummary(
                segment = segment,
                effortCount = row.effortCount,
                bestElapsed = row.bestElapsedMillis?.milliseconds,
                bestActivityId = row.bestActivityId,
                bestAt = row.bestStartEpochMillis?.let(Instant::ofEpochMilli),
            )
        }
    }

    override fun observeSegment(id: Long): Flow<Segment?> = dao.observeById(id).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeEffortsOfSegment(segmentId: Long): Flow<List<SegmentEffort>> =
        dao.observeEffortsOfSegment(segmentId).map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeEffortsOfActivity(activityId: Long): Flow<List<SegmentEffort>> =
        dao.observeEffortsOfActivity(activityId).map { rows -> rows.mapNotNull { it.toDomain() } }.distinctUntilChanged()

    override fun observePersonalRecords(): Flow<List<SegmentEffort>> =
        dao.observeBestEfforts().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun create(activityId: Long, startIndex: Int, endIndex: Int, name: String, sport: SportType): Long {
        val points = activityDao.getPoints(activityId)
        require(startIndex in points.indices && endIndex in points.indices && startIndex < endIndex) { "invalid range" }
        val domain = points.map { it.toDomain() }
        val draft = SegmentMath.draft(domain, startIndex, endIndex)
        require(draft.isLongEnough) { "a segment must be at least ${SegmentRules.MIN_LENGTH_METERS.toInt()} m long" }
        // Paused samples inside the range are where the athlete stood; they add nothing to the line.
        val kept = (startIndex..endIndex)
            .filter { i -> i == startIndex || i == endIndex || !points[i].paused }
            .dedupeBy { LatLng(points[it].latitude, points[it].longitude) }
        val latLngs = kept.map { LatLng(points[it].latitude, points[it].longitude) }
        require(latLngs.size >= 2) { "the range has no distance" }
        val simplified = DouglasPeucker.simplifyIndices(latLngs, SIMPLIFY_METERS)
        val line = simplified.map { latLngs[it] }
        val keptElevations = kept.map { points[it].elevationMeters }
        val elevations = if (draft.hasElevation && keptElevations.size == latLngs.size && keptElevations.all { it != null }) {
            simplified.joinToString(",") { "%.1f".format(java.util.Locale.ROOT, keptElevations[it]) }
        } else {
            ""
        }
        val shape = SegmentShape(line)
        val now = clock.millis()
        return dao.insert(
            SegmentEntity(
                uuid = UUID.randomUUID().toString(),
                name = cleanName(name).ifEmpty { "Segment" },
                sport = sport.name,
                polyline = PolylineCodec.encode(line),
                elevations = elevations,
                distanceMeters = draft.distanceMeters,
                elevationGainMeters = draft.elevationGainMeters,
                averageGradePercent = draft.averageGradePercent,
                startLatitude = shape.start.latitude,
                startLongitude = shape.start.longitude,
                endLatitude = shape.finish.latitude,
                endLongitude = shape.finish.longitude,
                bboxSouth = shape.south,
                bboxWest = shape.west,
                bboxNorth = shape.north,
                bboxEast = shape.east,
                sourceActivityId = activityId,
                // Strava stars the segments you create, so they show up while recording straight away.
                starred = true,
                createdAtMillis = now,
            ),
        )
    }

    override suspend fun rename(id: Long, name: String): Boolean {
        val clean = cleanName(name)
        if (clean.isEmpty()) return false
        return dao.rename(id, clean) > 0
    }

    override suspend fun setStarred(id: Long, starred: Boolean) {
        dao.setStarred(id, starred)
    }

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    override suspend fun matchActivity(activityId: Long): List<SegmentEffort> {
        val row = dao.matchRow(activityId) ?: return emptyList()
        val sport = sportOf(row.sport) ?: return emptyList()
        val south = row.bboxSouth ?: return emptyList()
        val west = row.bboxWest ?: return emptyList()
        val north = row.bboxNorth ?: return emptyList()
        val east = row.bboxEast ?: return emptyList()
        val family = EffortRanking.poolOf(sport).map { it.name }.toSet()
        val candidates = dao.getAll()
            .filter { it.sport in family }
            .mapNotNull { entity -> shapeOf(entity)?.let { entity to it } }
            .filter { (_, shape) -> shape.overlaps(south, west, north, east, config.gateMeters) }
        if (candidates.isEmpty()) return emptyList()
        val points = activityDao.getPoints(activityId)
        if (points.size < 2) return emptyList()
        val track = points.toTrack()
        val added = ArrayList<Long>()
        for ((segment, shape) in candidates) {
            val matches = SegmentMatcher.match(track, shape, config)
            if (matches.isEmpty()) continue
            val existing = dao.getEffortsOfSegment(segment.id)
            if (existing.any { it.activityId == activityId }) continue
            val efforts = buildEfforts(segment.id, row, points, matches, existing.map { SegmentRanking.Entry(it.elapsedMillis, it.year) })
            dao.insertEfforts(efforts).filter { it > 0 }.let(added::addAll)
        }
        if (added.isEmpty()) return emptyList()
        return dao.getEffortRows(added).mapNotNull { it.toDomain() }
    }

    override suspend fun rematchSegment(segmentId: Long): Int {
        val segment = dao.getById(segmentId) ?: return 0
        val sport = sportOf(segment.sport) ?: return 0
        val shape = shapeOf(segment) ?: return 0
        val margin = config.gateMeters / METERS_PER_DEGREE
        val candidates = dao.candidateActivities(
            EffortRanking.poolOf(sport).map { it.name },
            south = shape.south - margin,
            west = shape.west - margin * 2,
            north = shape.north + margin,
            east = shape.east + margin * 2,
        )
        val all = ArrayList<SegmentEffortEntity>()
        for (row in candidates) {
            val points = activityDao.getPoints(row.id)
            if (points.size < 2) continue
            val matches = SegmentMatcher.match(points.toTrack(), shape, config)
            if (matches.isEmpty()) continue
            all += buildEfforts(segmentId, row, points, matches, all.map { SegmentRanking.Entry(it.elapsedMillis, it.year) })
        }
        dao.replaceEffortsOfSegment(segmentId, all)
        return all.size
    }

    override suspend fun liveSegments(sport: SportType): List<LiveSegment> =
        dao.getStarred(EffortRanking.poolOf(sport).map { it.name }).mapNotNull { entity ->
            val shape = shapeOf(entity) ?: return@mapNotNull null
            val pr = dao.getEffortsOfSegment(entity.id).minOfOrNull { it.elapsedMillis }
            LiveSegment(entity.id, entity.name, shape, pr)
        }

    /** Effort rows for [matches] in one activity, ranked one after the other against [earlier]. */
    private fun buildEfforts(
        segmentId: Long,
        row: ActivityMatchRow,
        points: List<ActivityPointEntity>,
        matches: List<SegmentMatch>,
        earlier: List<SegmentRanking.Entry>,
    ): List<SegmentEffortEntity> {
        val zone = zoneOf(row.timeZoneId)
        val now = clock.millis()
        val timed = matches.map { m ->
            val first = points[m.startIndex]
            val startEpoch = first.epochMillis + (m.startOffsetMillis - first.offsetMillis).roundToLong()
            Triple(m, startEpoch, Instant.ofEpochMilli(startEpoch).atZone(zone).year)
        }
        val places = SegmentRanking.placeInOrder(earlier, timed.map { (m, _, year) -> SegmentRanking.Entry(m.elapsedMillis.roundToLong(), year) })
        return timed.mapIndexed { i, (m, startEpoch, year) ->
            val range = points.subList(m.startIndex, m.endIndex + 1)
            val heartRates = range.mapNotNull { it.heartRate }
            val power = range.mapNotNull { it.powerWatts }
            val cadence = range.mapNotNull { it.cadence }
            val elapsed = m.elapsedMillis.roundToLong()
            SegmentEffortEntity(
                segmentId = segmentId,
                activityId = row.id,
                startIndex = m.startIndex,
                endIndex = m.endIndex,
                startOffsetMillis = m.startOffsetMillis.roundToLong(),
                startEpochMillis = startEpoch,
                year = year,
                elapsedMillis = elapsed,
                movingMillis = m.movingMillis.roundToLong(),
                distanceMeters = m.distanceMeters,
                averageSpeedMps = if (elapsed > 0) m.distanceMeters / (elapsed / 1000.0) else 0.0,
                averageHeartRate = heartRates.takeIf { it.isNotEmpty() }?.average(),
                maxHeartRate = heartRates.maxOrNull(),
                averagePower = power.takeIf { it.isNotEmpty() }?.average(),
                averageCadence = cadence.takeIf { it.isNotEmpty() }?.average(),
                rankAllTime = places[i].rankAllTime,
                rankYear = places[i].rankYear,
                isPersonalRecord = places[i].isPersonalRecord,
                createdAtMillis = now,
            )
        }
    }

    private fun shapeOf(entity: SegmentEntity): SegmentShape? = try {
        SegmentShape(PolylineCodec.decode(entity.polyline))
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    private fun SegmentEntity.toDomain(): Segment? {
        val points = try {
            PolylineCodec.decode(polyline)
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: IndexOutOfBoundsException) {
            return null
        }
        val heights = elevations.split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == points.size && it.isNotEmpty() }
        return Segment(
            id = id,
            name = name,
            sport = sportOf(sport) ?: SportType.RUN,
            points = points,
            elevations = heights,
            distanceMeters = distanceMeters,
            elevationGainMeters = elevationGainMeters,
            averageGradePercent = averageGradePercent,
            starred = starred,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            sourceActivityId = sourceActivityId,
        )
    }

    private fun SegmentEffortRow.toDomain(): SegmentEffort? {
        val e = effort
        return SegmentEffort(
            id = e.id,
            segmentId = e.segmentId,
            segmentName = segmentName,
            segmentSport = sportOf(segmentSport) ?: return null,
            segmentDistanceMeters = segmentDistanceMeters,
            segmentGradePercent = segmentGradePercent,
            activityId = e.activityId,
            activityTitle = activityTitle,
            activitySport = sportOf(activitySport) ?: return null,
            startTime = Instant.ofEpochMilli(e.startEpochMillis),
            timeZone = zoneOf(timeZoneId),
            year = e.year,
            startIndex = e.startIndex,
            endIndex = e.endIndex,
            elapsed = e.elapsedMillis.milliseconds,
            moving = e.movingMillis.milliseconds,
            distanceMeters = e.distanceMeters,
            averageSpeedMps = e.averageSpeedMps,
            averageHeartRate = e.averageHeartRate,
            maxHeartRate = e.maxHeartRate,
            averagePower = e.averagePower,
            averageCadence = e.averageCadence,
            rankAllTime = e.rankAllTime,
            rankYear = e.rankYear,
            isPersonalRecord = e.isPersonalRecord,
        )
    }

    private companion object {
        /** The stored line keeps every bend wider than this; matching works at 25-30 m. */
        const val SIMPLIFY_METERS = 2.0
        const val METERS_PER_DEGREE = Spherical.EARTH_RADIUS_M * kotlin.math.PI / 180.0

        fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(SegmentRules.MAX_NAME_LENGTH)

        /** Drops entries whose key repeats the previous one's (a stationary GPS point). */
        fun <T, K> List<T>.dedupeBy(key: (T) -> K): List<T> = filterIndexed { i, v -> i == 0 || key(v) != key(this[i - 1]) }

        fun List<ActivityPointEntity>.toTrack() = SegmentTrack(
            latitudes = DoubleArray(size) { this[it].latitude },
            longitudes = DoubleArray(size) { this[it].longitude },
            offsetMillis = LongArray(size) { this[it].offsetMillis },
            distanceMeters = DoubleArray(size) { this[it].distanceMeters },
            paused = BooleanArray(size) { this[it].paused },
        )
    }
}
