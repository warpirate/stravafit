// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.style

/**
 * Builds a minimal MapLibre style (version 8) that shows one raster tile source, for base maps that
 * are published as tiles rather than as a vector style.
 */
object RasterStyleJson {
    /** Source id of the raster tiles in the generated style. */
    const val SOURCE_ID: String = "basemap-raster"

    /** Layer id of the raster tiles in the generated style. */
    const val LAYER_ID: String = "basemap-raster"

    /**
     * @param name style name.
     * @param tileUrls `{z}/{x}/{y}` URL templates; must not be empty.
     * @param maxZoom highest zoom the server provides; MapLibre overzooms beyond it.
     * @param tileSize tile edge in pixels as served (256 for classic raster tiles).
     * @param attribution credits embedded in the source.
     */
    fun build(
        name: String,
        tileUrls: List<String>,
        maxZoom: Int,
        attribution: List<AttributionPart>,
        tileSize: Int = 256,
    ): String {
        require(tileUrls.isNotEmpty()) { "at least one tile URL is required" }
        require(maxZoom in 0..24) { "maxZoom out of range: $maxZoom" }
        val credits = attribution.joinToString(" ") { "<a href=\"${it.url}\">${it.text}</a>" }
        return buildString {
            append("{\"version\":8,\"name\":").append(quote(name))
            append(",\"sources\":{").append(quote(SOURCE_ID)).append(":{")
            append("\"type\":\"raster\",\"tiles\":[")
            tileUrls.forEachIndexed { i, url ->
                if (i > 0) append(',')
                append(quote(url))
            }
            append("],\"tileSize\":").append(tileSize)
            append(",\"maxzoom\":").append(maxZoom)
            append(",\"attribution\":").append(quote(credits))
            append("}},\"layers\":[")
            append("{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#f2efe9\"}},")
            append("{\"id\":").append(quote(LAYER_ID)).append(",\"type\":\"raster\",\"source\":").append(quote(SOURCE_ID))
            append("}]}")
        }
    }

    /** JSON string literal for [s]. */
    internal fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }
}
