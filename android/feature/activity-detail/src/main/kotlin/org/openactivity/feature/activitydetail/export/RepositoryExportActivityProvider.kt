// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.export

import kotlinx.coroutines.flow.first
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.SavedPrivacyZone
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.PrivacyZoneRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.export.art.ArtUnits
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.core.export.model.ExportPrivacy
import org.openactivity.core.export.model.ExportPrivacyZone
import org.openactivity.core.export.model.ExportStats
import org.openactivity.feature.shareexport.ExportActivityProvider
import org.openactivity.model.UnitSystem
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * [ExportActivityProvider] over the local database: the share screen and file export read the same
 * stored samples and summary the detail screen shows. Activity ids are the database ids as strings.
 * The owner's privacy zones ride along, so the share screen's privacy trimming applies them with the
 * activity's own start/end hiding (one trimming path for every image, sticker and file).
 */
@Singleton
class RepositoryExportActivityProvider @Inject constructor(
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
    private val zones: PrivacyZoneRepository,
) : ExportActivityProvider {

    override suspend fun load(activityId: String): ExportActivity? {
        val id = activityId.toLongOrNull() ?: return null
        val detail = activities.observeActivityDetail(id).first() ?: return null
        return toExport(detail, zones.zones())
    }

    override suspend fun units(): ArtUnits =
        if (settings.settings.first().units == UnitSystem.IMPERIAL) ArtUnits.IMPERIAL else ArtUnits.METRIC

    companion object {
        /**
         * Maps a stored activity to the export model. Samples taken while paused are left out: they are
         * kept in the database for cropping but are not part of the activity.
         */
        fun toExport(detail: ActivityDetail, zones: List<SavedPrivacyZone> = emptyList()): ExportActivity {
            val a = detail.activity
            return ExportActivity(
                id = a.id.toString(),
                title = a.title,
                sport = a.sport,
                startTime = a.startTime,
                points = detail.points.filterNot { it.paused }.map { p ->
                    ExportPoint(
                        latitude = p.latitude,
                        longitude = p.longitude,
                        elevation = p.elevationMeters,
                        time = Instant.ofEpochMilli(p.epochMillis),
                        heartRate = p.heartRate,
                        cadence = p.cadence?.roundToInt(),
                        power = p.powerWatts,
                        distance = p.distanceMeters,
                    )
                },
                stats = ExportStats(
                    distanceMeters = a.distanceMeters,
                    movingTimeSeconds = Math.round(a.movingTime.inWholeMilliseconds / 1000.0),
                    elapsedTimeSeconds = Math.round(a.elapsedTime.inWholeMilliseconds / 1000.0),
                    movingTimeExactSeconds = a.movingTime.inWholeMilliseconds / 1000.0,
                    elevationGainMeters = a.elevationGainMeters.takeIf { detail.points.any { p -> p.elevationMeters != null } },
                    averageHeartRate = a.averageHeartRate?.roundToInt(),
                    calories = null,
                ),
                zone = a.timeZone,
                privacy = ExportPrivacy(
                    hideStartMeters = a.hideStartMeters,
                    hideEndMeters = a.hideEndMeters,
                    zones = zones.map { ExportPrivacyZone(it.center.latitude, it.center.longitude, it.radiusMeters.toDouble()) },
                ),
            )
        }
    }
}
