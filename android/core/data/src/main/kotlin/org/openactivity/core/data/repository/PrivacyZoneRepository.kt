// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.model.PrivacyZoneError
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.data.model.SavedPrivacyZone
import org.openactivity.core.database.dao.PrivacyZoneDao
import org.openactivity.core.database.entity.PrivacyZoneEntity
import org.openactivity.model.LatLng
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The owner's privacy zones, kept on the device (and in backups, with the rest of the database). */
interface PrivacyZoneRepository {
    /** Every zone, oldest first. */
    fun observeZones(): Flow<List<SavedPrivacyZone>>

    /** The zones as they are now, for one-off reads such as building a shared image. */
    suspend fun zones(): List<SavedPrivacyZone>

    suspend fun get(id: Long): SavedPrivacyZone?

    /**
     * Adds a zone. [radiusMeters] is snapped to the nearest allowed radius and [name] trimmed.
     *
     * @return the new id, or [PrivacyZoneError.LIMIT_REACHED].
     */
    suspend fun add(name: String, center: LatLng, radiusMeters: Int): Result<Long>

    /** Changes a zone; fails with [PrivacyZoneError.NOT_FOUND] when it was deleted. */
    suspend fun update(id: Long, name: String, center: LatLng, radiusMeters: Int): Result<Long>

    /** Deletes a zone. Returns false when it did not exist. */
    suspend fun delete(id: Long): Boolean
}

/** A save that failed for [error]. */
class PrivacyZoneException(val error: PrivacyZoneError) : Exception(error.name)

/** [PrivacyZoneRepository] over Room. */
@Singleton
class LocalPrivacyZoneRepository @Inject constructor(
    private val dao: PrivacyZoneDao,
    @DataClock private val clock: Clock,
) : PrivacyZoneRepository {

    override fun observeZones(): Flow<List<SavedPrivacyZone>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun zones(): List<SavedPrivacyZone> = dao.getAll().map { it.toDomain() }

    override suspend fun get(id: Long): SavedPrivacyZone? = dao.getById(id)?.toDomain()

    override suspend fun add(name: String, center: LatLng, radiusMeters: Int): Result<Long> {
        val now = clock.millis()
        val row = PrivacyZoneEntity(
            uuid = UUID.randomUUID().toString(),
            label = cleanName(name),
            centerLatitude = center.latitude,
            centerLongitude = center.longitude,
            radiusMeters = snapRadius(radiusMeters),
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val id = dao.insertWithin(row, PrivacyZones.MAX_ZONES) ?: return Result.failure(PrivacyZoneException(PrivacyZoneError.LIMIT_REACHED))
        return Result.success(id)
    }

    override suspend fun update(id: Long, name: String, center: LatLng, radiusMeters: Int): Result<Long> {
        val existing = dao.getById(id) ?: return Result.failure(PrivacyZoneException(PrivacyZoneError.NOT_FOUND))
        val changed = dao.update(
            existing.copy(
                label = cleanName(name),
                centerLatitude = center.latitude,
                centerLongitude = center.longitude,
                radiusMeters = snapRadius(radiusMeters),
                updatedAtMillis = clock.millis(),
            ),
        )
        return if (changed > 0) Result.success(id) else Result.failure(PrivacyZoneException(PrivacyZoneError.NOT_FOUND))
    }

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    internal companion object {
        /** The allowed radius closest to [meters]. */
        fun snapRadius(meters: Int): Int = PrivacyZones.RADII_METERS.minBy { kotlin.math.abs(it - meters) }

        fun cleanName(name: String): String? = name.trim().take(PrivacyZones.MAX_NAME_LENGTH).ifEmpty { null }

        fun PrivacyZoneEntity.toDomain() = SavedPrivacyZone(
            id = id,
            uuid = uuid,
            name = label.orEmpty(),
            center = LatLng(centerLatitude, centerLongitude),
            // A radius written by a newer or older version still maps onto a choice the screen offers.
            radiusMeters = snapRadius(radiusMeters),
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtMillis),
        )
    }
}
