// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.journal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.core.database.dao.RecordingJournalDao
import org.openactivity.core.database.entity.JournalPointEntity
import org.openactivity.core.database.entity.RecordingJournalEntity
import org.openactivity.model.SportType
import org.openactivity.recording.EngineSnapshot
import org.openactivity.recording.TrackPoint
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A journalled recording read back after the process died.
 *
 * @property snapshot the engine state including all journalled points.
 */
data class JournalContents(
    val recordingUuid: String,
    val sport: SportType,
    val routeId: Long?,
    val startedAtMillis: Long,
    val lastWallClockMillis: Long,
    val snapshot: EngineSnapshot,
)

/** Header of the journal row, cheap to observe (no snapshot decoding). */
data class JournalHeader(
    val recordingUuid: String,
    val sport: SportType,
    val startedAtMillis: Long,
    val lastWallClockMillis: Long,
)

/**
 * Write-ahead journal of the in-progress recording (recording design §16.2).
 *
 * The engine snapshot is stored without its track points; points are appended incrementally, so a flush
 * costs the few points recorded since the previous one plus a small snapshot blob. Both go in one
 * transaction, so the journal is always consistent.
 */
@Singleton
class RecordingJournal @Inject constructor(private val dao: RecordingJournalDao) {
    private var persistedPoints = 0

    /** Header of the current journal, or null when there is none. */
    val header: Flow<JournalHeader?> = dao.observe().map { row ->
        row?.let { JournalHeader(it.recordingUuid, sportOf(it.sport), it.startedAtMillis, it.lastWallClockMillis) }
    }

    /** Starts a journal for a new recording, replacing any older one. */
    suspend fun begin(
        recordingUuid: String,
        sport: SportType,
        routeId: Long?,
        snapshot: EngineSnapshot,
        monotonicNanos: Long,
        wallClockMillis: Long,
    ) {
        persistedPoints = 0
        dao.begin(row(recordingUuid, sport, routeId, wallClockMillis, snapshot, 0, monotonicNanos, wallClockMillis))
        flush(recordingUuid, sport, routeId, wallClockMillis, snapshot, monotonicNanos, wallClockMillis)
    }

    /** Continues journalling a recording restored from this journal (its points are already stored). */
    fun resume(pointCount: Int) {
        persistedPoints = pointCount
    }

    /** Writes the points recorded since the last flush and the new snapshot, atomically. */
    suspend fun flush(
        recordingUuid: String,
        sport: SportType,
        routeId: Long?,
        startedAtMillis: Long,
        snapshot: EngineSnapshot,
        monotonicNanos: Long,
        wallClockMillis: Long,
    ) {
        val points = snapshot.points
        // Points are append-only while recording; if that ever fails to hold, rewrite them all.
        val from = if (points.size >= persistedPoints) persistedPoints else 0
        val newPoints = points.subList(from, points.size).mapIndexed { i, p -> p.toJournal(from + i) }
        dao.flush(
            row(recordingUuid, sport, routeId, startedAtMillis, snapshot, points.size, monotonicNanos, wallClockMillis),
            newPoints,
        )
        persistedPoints = points.size
    }

    /**
     * Reads the journal back. Returns null when there is none, and clears (returns null for) a journal that
     * cannot be decoded, since it can never be restored.
     */
    suspend fun load(): JournalContents? {
        val row = dao.get() ?: return null
        val snapshot = try {
            if (row.snapshotFormat != SnapshotCodec.FORMAT) throw IOException("format ${row.snapshotFormat}")
            SnapshotCodec.decode(row.engineSnapshot)
        } catch (e: IOException) {
            clear()
            return null
        }
        val points = dao.getPoints(row.pointCount).map { it.toTrackPoint() }
        return JournalContents(
            recordingUuid = row.recordingUuid,
            sport = sportOf(row.sport),
            routeId = row.routeId,
            startedAtMillis = row.startedAtMillis,
            lastWallClockMillis = row.lastWallClockMillis,
            snapshot = snapshot.copy(points = points),
        )
    }

    /** Removes the journal (after saving or discarding). */
    suspend fun clear() {
        persistedPoints = 0
        dao.clear()
    }

    private fun row(
        uuid: String,
        sport: SportType,
        routeId: Long?,
        startedAtMillis: Long,
        snapshot: EngineSnapshot,
        pointCount: Int,
        monotonicNanos: Long,
        wallClockMillis: Long,
    ) = RecordingJournalEntity(
        recordingUuid = uuid,
        sport = sport.name,
        routeId = routeId,
        startedAtMillis = startedAtMillis,
        snapshotFormat = SnapshotCodec.FORMAT,
        engineSnapshot = SnapshotCodec.encode(snapshot.copy(points = emptyList())),
        pointCount = pointCount,
        lastMonotonicNanos = monotonicNanos,
        lastWallClockMillis = wallClockMillis,
    )

    private fun sportOf(name: String): SportType = SportType.entries.firstOrNull { it.name == name } ?: SportType.OTHER
}

internal fun TrackPoint.toJournal(seq: Int) = JournalPointEntity(
    seq = seq,
    offsetNanos = offsetNanos,
    epochMillis = epochMillis,
    latitude = latitude,
    longitude = longitude,
    rawLatitude = rawLatitude,
    rawLongitude = rawLongitude,
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMps = speedMps,
    distanceMeters = distanceMeters,
    paused = paused,
    segment = segment,
    heartRate = heartRateBpm,
    cadence = cadence,
    powerWatts = powerWatts,
)

internal fun JournalPointEntity.toTrackPoint() = TrackPoint(
    offsetNanos = offsetNanos,
    epochMillis = epochMillis,
    latitude = latitude,
    longitude = longitude,
    rawLatitude = rawLatitude,
    rawLongitude = rawLongitude,
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMps = speedMps,
    distanceMeters = distanceMeters,
    paused = paused,
    segment = segment,
    heartRateBpm = heartRate,
    cadence = cadence,
    powerWatts = powerWatts,
)
