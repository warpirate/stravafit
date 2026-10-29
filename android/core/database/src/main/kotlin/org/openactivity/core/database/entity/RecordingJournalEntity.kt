// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The in-progress recording, rewritten every few seconds so a recording survives process death.
 *
 * At most one row exists. [engineSnapshot] holds the serialized engine state without its track points;
 * those are appended to `recording_journal_points` instead, so each flush writes only what is new.
 *
 * @property recordingUuid becomes the saved activity's uuid; recovery skips a journal whose activity exists.
 * @property snapshotFormat version of the [engineSnapshot] encoding.
 * @property pointCount number of journal points covered by [engineSnapshot].
 * @property lastMonotonicNanos device monotonic time of the last flush.
 * @property lastWallClockMillis wall-clock time of the last flush.
 */
@Entity(tableName = "recordings_journal")
data class RecordingJournalEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "recording_uuid") val recordingUuid: String,
    val sport: String,
    @ColumnInfo(name = "route_id") val routeId: Long?,
    @ColumnInfo(name = "started_at_millis") val startedAtMillis: Long,
    @ColumnInfo(name = "snapshot_format") val snapshotFormat: Int,
    @ColumnInfo(name = "engine_snapshot", typeAffinity = ColumnInfo.BLOB) val engineSnapshot: ByteArray,
    @ColumnInfo(name = "point_count") val pointCount: Int,
    @ColumnInfo(name = "last_monotonic_nanos") val lastMonotonicNanos: Long,
    @ColumnInfo(name = "last_wall_clock_millis") val lastWallClockMillis: Long,
) {
    override fun equals(other: Any?): Boolean = other is RecordingJournalEntity &&
        id == other.id && recordingUuid == other.recordingUuid && sport == other.sport &&
        routeId == other.routeId && startedAtMillis == other.startedAtMillis &&
        snapshotFormat == other.snapshotFormat && engineSnapshot.contentEquals(other.engineSnapshot) &&
        pointCount == other.pointCount && lastMonotonicNanos == other.lastMonotonicNanos &&
        lastWallClockMillis == other.lastWallClockMillis

    override fun hashCode(): Int = recordingUuid.hashCode() * 31 + engineSnapshot.contentHashCode()

    companion object {
        /** Primary key of the single journal row. */
        const val SINGLETON_ID: Int = 1
    }
}

/** One accepted track point of the in-progress recording (mirrors the engine's `TrackPoint`). */
@Entity(
    tableName = "recording_journal_points",
    primaryKeys = ["journal_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = RecordingJournalEntity::class,
            parentColumns = ["id"],
            childColumns = ["journal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class JournalPointEntity(
    @ColumnInfo(name = "journal_id") val journalId: Int = RecordingJournalEntity.SINGLETON_ID,
    val seq: Int,
    @ColumnInfo(name = "offset_nanos") val offsetNanos: Long,
    @ColumnInfo(name = "epoch_millis") val epochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "raw_latitude") val rawLatitude: Double,
    @ColumnInfo(name = "raw_longitude") val rawLongitude: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Double?,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Double?,
    @ColumnInfo(name = "speed_mps") val speedMps: Double,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    val paused: Boolean,
    val segment: Int,
    @ColumnInfo(name = "heart_rate") val heartRate: Int?,
    val cadence: Double?,
    @ColumnInfo(name = "power_watts") val powerWatts: Int?,
)
