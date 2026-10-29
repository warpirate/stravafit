// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved route (imported from a GPX file or created in the app).
 *
 * @property sport sport the route is meant for, by `SportType.name`, or null when unknown.
 * @property polyline encoded polyline (precision 5) of the simplified route.
 */
@Entity(tableName = "routes", indices = [Index(value = ["uuid"], unique = true)])
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val description: String,
    val sport: String?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "elevation_gain_meters") val elevationGainMeters: Double,
    @ColumnInfo(name = "elevation_loss_meters") val elevationLossMeters: Double,
    val polyline: String,
    @ColumnInfo(name = "bbox_south") val bboxSouth: Double,
    @ColumnInfo(name = "bbox_west") val bboxWest: Double,
    @ColumnInfo(name = "bbox_north") val bboxNorth: Double,
    @ColumnInfo(name = "bbox_east") val bboxEast: Double,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

/** One point of a route, with cumulative distance for progress and off-route checks. */
@Entity(
    tableName = "route_points",
    primaryKeys = ["route_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RoutePointEntity(
    @ColumnInfo(name = "route_id") val routeId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "elevation_meters") val elevationMeters: Double?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
)
