// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import java.util.Locale

/**
 * Names a loop after the OpenStreetMap way it spends the most distance on ("Bund Garden Rd loop"), or by
 * length and direction when no named way covers enough of it ("5 km loop · NE").
 */
object RouteNamer {
    /** A way must cover at least this share of the loop to name it. */
    private const val MIN_SHARE = 0.10
    private const val MAX_NAME_LENGTH = 32

    fun name(roadNames: List<Pair<String, Double>>, routeMeters: Double, targetMeters: Double, bearingDeg: Double): String {
        val byName = roadNames.groupBy({ it.first.trim() }, { it.second }).mapValues { it.value.sum() }
        val best = byName.filterKeys { it.isNotEmpty() }.maxByOrNull { it.value }
        if (best != null && routeMeters > 0 && best.value / routeMeters >= MIN_SHARE) {
            val road = abbreviate(best.key)
            if (road.length <= MAX_NAME_LENGTH) return "$road loop"
        }
        return fallback(targetMeters, bearingDeg)
    }

    /** "5 km loop · NE". */
    fun fallback(targetMeters: Double, bearingDeg: Double): String = "${kilometres(targetMeters)} km loop · ${LoopGeometry.compass(bearingDeg)}"

    /** Common street-type abbreviations, as on signs and in Strava route names. */
    fun abbreviate(road: String): String = road.split(' ').joinToString(" ") { ABBREVIATIONS[it.lowercase(Locale.ROOT)] ?: it }

    private fun kilometres(meters: Double): String {
        val km = meters / 1000.0
        return if (km % 1.0 == 0.0) km.toLong().toString() else String.format(Locale.ROOT, "%.1f", km)
    }

    private val ABBREVIATIONS = mapOf(
        "road" to "Rd",
        "street" to "St",
        "avenue" to "Ave",
        "lane" to "Ln",
        "drive" to "Dr",
        "boulevard" to "Blvd",
    )
}
