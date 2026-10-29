// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.ui.graphics.vector.ImageVector
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.feature.shareexport.R

internal val ArtTemplate.icon: ImageVector
    get() = when (this) {
        ArtTemplate.MINIMAL_STICKER -> Icons.Rounded.Gesture
        ArtTemplate.AURORA -> Icons.Rounded.AutoAwesome
        ArtTemplate.POSTER -> Icons.AutoMirrored.Rounded.Article
        ArtTemplate.STORY -> Icons.Rounded.Smartphone
        ArtTemplate.ELEVATION -> Icons.Rounded.Landscape
    }

@get:StringRes
internal val ArtUnavailableReason.message: Int
    get() = when (this) {
        ArtUnavailableReason.NO_ROUTE -> R.string.share_export_no_route
        ArtUnavailableReason.ROUTE_HIDDEN_BY_PRIVACY -> R.string.share_export_route_hidden
        ArtUnavailableReason.NO_ELEVATION -> R.string.share_export_no_elevation
    }

@get:StringRes
internal val FormatUnavailableReason.message: Int
    get() = when (this) {
        FormatUnavailableReason.NEEDS_ROUTE -> R.string.share_export_needs_route
        FormatUnavailableReason.NEEDS_TIMES -> R.string.share_export_needs_times
        FormatUnavailableReason.ROUTE_HIDDEN -> R.string.share_export_route_hidden
        FormatUnavailableReason.NEEDS_ELEVATION -> R.string.share_export_no_elevation
    }

@get:StringRes
internal val ExportFormat.description: Int
    get() = when (this) {
        ExportFormat.GPX -> R.string.share_export_format_gpx
        ExportFormat.TCX -> R.string.share_export_format_tcx
        ExportFormat.FIT -> R.string.share_export_format_fit
        ExportFormat.GEOJSON -> R.string.share_export_format_geojson
        ExportFormat.SVG -> R.string.share_export_format_svg
        ExportFormat.PNG -> R.string.share_export_format_png
        ExportFormat.PDF -> R.string.share_export_format_pdf
    }
