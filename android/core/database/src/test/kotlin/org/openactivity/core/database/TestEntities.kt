// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.JournalPointEntity
import org.openactivity.core.database.entity.RecordingJournalEntity

/** Builders for test rows with sensible defaults. */
internal object TestEntities {
    fun activity(
        uuid: String = "uuid-1",
        sport: String = "RUN",
        start: Long = 1_700_000_000_000L,
        distance: Double = 5_000.0,
        moving: Long = 1_500_000L,
        title: String = "Morning Run",
    ) = ActivityEntity(
        uuid = uuid,
        sport = sport,
        title = title,
        description = "",
        visibility = "FOLLOWERS",
        perceivedExertion = null,
        startEpochMillis = start,
        timeZoneId = "UTC",
        elapsedMillis = moving + 60_000L,
        movingMillis = moving,
        distanceMeters = distance,
        elevationGainMeters = 42.0,
        elevationLossMeters = 40.0,
        altitudeSource = "GPS",
        averageSpeedMps = distance / (moving / 1000.0),
        maxSpeedMps = 5.0,
        averageGradeAdjustedSpeedMps = null,
        averageHeartRate = null,
        maxHeartRate = null,
        averagePower = null,
        maxPower = null,
        averageCadence = null,
        autoPauseCount = 0,
        polyline = "_p~iF~ps|U_ulLnnqC",
        bboxSouth = 38.5,
        bboxWest = -120.2,
        bboxNorth = 40.7,
        bboxEast = -120.95,
        privacyHideStartMeters = 200.0,
        privacyHideEndMeters = 200.0,
        flags = "",
        routeId = null,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    fun point(seq: Int, activityId: Long = 0) = ActivityPointEntity(
        activityId = activityId,
        seq = seq,
        latitude = 47.0 + seq * 1e-4,
        longitude = 8.0,
        elevationMeters = 400.0 + seq,
        epochMillis = 1_700_000_000_000L + seq * 1000L,
        offsetMillis = seq * 1000L,
        heartRate = 140,
        cadence = 85.0,
        powerWatts = null,
        speedMps = 3.0,
        accuracyMeters = 4.0,
        distanceMeters = seq * 11.1,
        paused = false,
        segment = 0,
    )

    fun journal(pointCount: Int, snapshot: ByteArray = byteArrayOf(1, 2, 3)) = RecordingJournalEntity(
        recordingUuid = "rec-1",
        sport = "RUN",
        routeId = null,
        startedAtMillis = 1_700_000_000_000L,
        snapshotFormat = 1,
        engineSnapshot = snapshot,
        pointCount = pointCount,
        lastMonotonicNanos = 5_000_000_000L,
        lastWallClockMillis = 1_700_000_005_000L,
    )

    fun journalPoint(seq: Int) = JournalPointEntity(
        seq = seq,
        offsetNanos = seq * 1_000_000_000L,
        epochMillis = 1_700_000_000_000L + seq * 1000L,
        latitude = 47.0,
        longitude = 8.0 + seq * 1e-4,
        rawLatitude = 47.0,
        rawLongitude = 8.0 + seq * 1e-4,
        accuracyMeters = 5.0,
        altitudeMeters = null,
        speedMps = 3.0,
        distanceMeters = seq * 7.6,
        paused = false,
        segment = 0,
        heartRate = null,
        cadence = null,
        powerWatts = null,
    )
}
