// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.runtime.Immutable
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.feature.activitydetail.chart.ChartData
import org.openactivity.feature.activitydetail.export.FileFormatOption
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility

/** State of the activity detail screen. */
sealed interface ActivityDetailUiState {
    /** Reading the activity. */
    data object Loading : ActivityDetailUiState

    /** The activity does not exist (deleted elsewhere, or a stale link). */
    data object NotFound : ActivityDetailUiState

    /** The activity is shown. */
    data class Content(val activity: DetailContent, val dialogs: DetailDialogs = DetailDialogs()) : ActivityDetailUiState
}

/**
 * Everything the detail screen draws for one activity.
 *
 * @property route the track for the map; empty for activities without GPS.
 * @property routeSpeeds per-point speed for the pace-coloured line, same size as [route].
 * @property routeDistances distance from the start of each route point, same size as [route].
 * @property chart shared chart samples, or null when there are not enough samples.
 * @property splitUnit "Km" or "Mi", the column header of the splits table.
 * @property notes quality and privacy notes shown under the stats.
 */
@Immutable
data class DetailContent(
    val id: Long,
    val title: String,
    val sport: SportType,
    val sportName: String,
    val startedAt: String,
    val description: String,
    val visibility: Visibility,
    val perceivedExertion: Int?,
    val muted: Boolean,
    val photos: List<ActivityPhoto>,
    val units: UnitSystem,
    val stats: List<Stat>,
    val route: List<LatLng>,
    val routeSpeeds: List<Double?>,
    val routeDistances: List<Double>,
    val chart: ChartData?,
    val splits: List<SplitRow>,
    val splitUnit: String,
    val laps: List<LapRow>,
    val efforts: List<EffortRow>,
    val showEfforts: Boolean,
    val notes: List<String>,
) {
    /** True when there is a route to draw on the map. */
    val hasRoute: Boolean get() = route.size >= 2

    /** The part of the route between [startMeters] and [endMeters] from the start. */
    fun routeBetween(startMeters: Double, endMeters: Double): List<LatLng> =
        route.filterIndexed { i, _ -> routeDistances[i] in startMeters..endMeters }
}

/**
 * Dialogs and sheets currently open.
 *
 * @property exportFormats the export-file sheet's options when it is open, else null.
 * @property exporting true while a file is being written.
 */
@Immutable
data class DetailDialogs(
    val confirmDelete: Boolean = false,
    val exportFormats: List<FileFormatOption>? = null,
    val exporting: Boolean = false,
)

/** One-shot effects for the screen. */
sealed interface DetailEffect {
    /** The activity was deleted; leave the screen. */
    data object Deleted : DetailEffect

    /** Ask the user where to save [file] (create-document picker). */
    data class SaveDocument(val file: ExportedFile) : DetailEffect

    /** Open the system share sheet for [file]. */
    data class ShareFile(val file: ExportedFile) : DetailEffect

    /** Show a short [message]. */
    data class Message(val message: String) : DetailEffect
}
