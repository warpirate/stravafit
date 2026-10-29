// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.file

/**
 * File formats the export sheet offers.
 *
 * @property extension file-name extension without the dot.
 * @property mimeType MIME type announced to receiving apps.
 * @property isData true for activity data (track files), false for images and documents.
 */
public enum class ExportFormat(
    public val label: String,
    public val extension: String,
    public val mimeType: String,
    public val isData: Boolean,
) {
    GPX("GPX", "gpx", "application/gpx+xml", isData = true),
    TCX("TCX", "tcx", "application/vnd.garmin.tcx+xml", isData = true),
    FIT("FIT", "fit", "application/vnd.ant.fit", isData = true),
    GEOJSON("GeoJSON", "geojson", "application/geo+json", isData = true),
    SVG("SVG image", "svg", "image/svg+xml", isData = false),
    PNG("PNG image", "png", "image/png", isData = false),
    PDF("PDF poster", "pdf", "application/pdf", isData = false),
    ;

    public companion object {
        /** Track-data formats, in the order the sheet lists them. */
        public val DATA: List<ExportFormat> = entries.filter { it.isData }
    }
}
