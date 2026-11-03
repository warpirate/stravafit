// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A privacy zone (a hidden place such as home or work): routes that start or end inside it are trimmed
 * before anything leaves the phone. Mirrors the backend table `public.privacy_zones` so sync can map it:
 * [uuid] is its `id`, [label] its `label`, the centre its `center` point and [radiusMeters] its `radius_m`.
 * Only the centre is stored, never an address.
 *
 * @property label the owner's name for the place ("Home"), at most 60 characters; null when unnamed.
 * @property radiusMeters 200, 400, 800 or 1600 (the backend allows 200..1600).
 */
@Entity(tableName = "privacy_zones", indices = [Index(value = ["uuid"], unique = true)])
data class PrivacyZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val label: String?,
    @ColumnInfo(name = "center_latitude") val centerLatitude: Double,
    @ColumnInfo(name = "center_longitude") val centerLongitude: Double,
    @ColumnInfo(name = "radius_meters") val radiusMeters: Int,
    @ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at_millis") val updatedAtMillis: Long,
)
