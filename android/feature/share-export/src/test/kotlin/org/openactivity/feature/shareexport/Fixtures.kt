// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import android.content.Context
import android.net.Uri
import org.openactivity.core.export.ActivityExporter
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.ExportPoint
import org.openactivity.core.export.model.ExportStats
import org.openactivity.model.SportType
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Generated test activities (not real recordings). */
object Fixtures {
    private val start: Instant = Instant.parse("2026-06-01T06:30:00Z")

    fun run(id: String = "run-1"): ExportActivity = ExportActivity(
        id = id,
        title = "Lake loop",
        sport = SportType.RUN,
        startTime = start,
        points = List(400) { i ->
            val t = 2 * PI * i / 400
            ExportPoint(
                latitude = 47.37 + 0.006 * sin(t),
                longitude = 8.53 + 0.009 * cos(t),
                elevation = 410.0 + 20 * sin(2 * t),
                time = start.plusSeconds(3L * i),
                heartRate = 140 + i % 20,
            )
        },
        stats = ExportStats(distanceMeters = 4980.0, movingTimeSeconds = 1197),
        zone = ZoneOffset.UTC,
    )

    fun indoor(id: String = "ride-1"): ExportActivity = ExportActivity(
        id = id,
        title = "Trainer",
        sport = SportType.VIRTUAL_RIDE,
        startTime = start,
        points = List(60) { ExportPoint(null, null, time = start.plusSeconds(it.toLong()), heartRate = 120) },
        stats = ExportStats(distanceMeters = 0.0, movingTimeSeconds = 59),
        zone = ZoneOffset.UTC,
    )

    /** An exporter whose content URIs work on any host (FileProvider needs '/' separators). */
    fun exporter(context: Context): ActivityExporter = ActivityExporter.create(
        ExportFileStore(context) { ctx, file -> Uri.parse("content://${ctx.packageName}.export.fileprovider/exports/${file.name}") },
    )
}

/** In-memory provider. */
class FakeProvider(private vararg val activities: ExportActivity) : ExportActivityProvider {
    var failNext: Exception? = null

    override suspend fun load(activityId: String): ExportActivity? {
        failNext?.let { failNext = null; throw it }
        return activities.firstOrNull { it.id == activityId }
    }
}
