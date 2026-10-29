// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.route

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.app.data.export.toExportActivity
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.TrackFileEncoder
import org.openactivity.core.fileimport.ActivityImporter
import org.openactivity.core.fileimport.FileImportResult
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.model.SportType
import java.io.IOException
import javax.inject.Inject

/**
 * Result of importing route files.
 *
 * @property importedIds ids of the saved routes, in file order.
 * @property failures one readable message per file or track that could not be used.
 */
data class RouteImportSummary(val importedIds: List<Long>, val failures: List<String>)

/** Imports GPX (or TCX/FIT) files as saved routes. */
interface RouteImporter {
    suspend fun import(uris: List<Uri>): RouteImportSummary
}

/** Writes a saved route to a file. */
interface RouteExporter {
    /**
     * Writes route [routeId] as GPX to the document at [destination].
     *
     * @throws IOException when the route is gone or the document cannot be written.
     */
    suspend fun exportGpx(routeId: Long, destination: Uri)
}

/** [RouteImporter] over the `:core:import` parsers and the `:core:data` [RouteRepository]. */
class FileRouteImporter @Inject constructor(
    private val importer: ActivityImporter,
    private val routes: RouteRepository,
) : RouteImporter {

    override suspend fun import(uris: List<Uri>): RouteImportSummary {
        val ids = mutableListOf<Long>()
        val failures = mutableListOf<String>()
        for (result in importer.importAll(uris)) {
            when (result) {
                is FileImportResult.Failure -> failures += "${result.displayName}: ${result.error.message}"
                is FileImportResult.Success -> result.activities.forEach { activity ->
                    val input = activity.toRouteInput()
                    if (input == null) {
                        failures += "${result.displayName}: no GPS track to follow"
                    } else {
                        try {
                            ids += routes.importRoute(input.name, input.points, sport = input.sport)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: RuntimeException) {
                            // A database failure on one file must not lose the routes already imported.
                            failures += "${result.displayName}: could not be saved"
                        }
                    }
                }
            }
        }
        return RouteImportSummary(ids, failures)
    }
}

/** What a parsed file contributes as a route. */
data class RouteInput(val name: String, val points: List<RoutePointInput>, val sport: SportType?)

/**
 * The route part of a parsed file: its positioned samples, its name (or the file name without
 * extension) and its sport when the file declares one. Null when fewer than two samples have a position.
 */
fun ImportedActivity.toRouteInput(): RouteInput? {
    val points = points.mapNotNull { p ->
        val lat = p.latitude ?: return@mapNotNull null
        val lon = p.longitude ?: return@mapNotNull null
        RoutePointInput(lat, lon, p.elevation)
    }
    if (points.size < 2) return null
    val name = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: sourceName.substringBeforeLast('.').removeSuffix(".gpx").removeSuffix(".tcx").removeSuffix(".fit").trim()
    return RouteInput(name, points, sport.takeIf { sportDeclared && it != SportType.OTHER })
}

/** [RouteExporter] writing GPX through the `:core:export` encoder. */
class GpxRouteExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routes: RouteRepository,
    private val encoder: TrackFileEncoder,
) : RouteExporter {
    override suspend fun exportGpx(routeId: Long, destination: Uri) {
        val route = routes.getRoute(routeId) ?: throw IOException("This route no longer exists")
        val bytes = encoder.encode(route.toExportActivity(), ExportFormat.GPX)
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot open the chosen file")
            output.use { it.write(bytes) }
        }
    }
}
