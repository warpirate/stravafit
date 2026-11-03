// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import org.openactivity.model.SportType
import java.io.Reader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * One row of a Strava export's `activities.csv`.
 *
 * @property id Strava's activity id, unique within the export.
 * @property start the "Activity Date" (UTC), null when it cannot be read.
 * @property typeName the "Activity Type" as written, for messages.
 * @property sport [typeName] mapped with [StravaSports].
 * @property fileName the activity's track file inside the export (`activities/123.fit.gz`), null for
 *   manual entries.
 * @property media photo files inside the export (`media/abc.jpg`).
 * @property distanceMeters total distance, null when the row does not say.
 * @property elapsedSeconds elapsed time, null when the row does not say.
 */
public data class StravaActivityRow(
    val id: String,
    val start: Instant?,
    val name: String,
    val typeName: String,
    val sport: SportType,
    val description: String,
    val fileName: String?,
    val media: List<String>,
    val distanceMeters: Double?,
    val elapsedSeconds: Long?,
    val movingSeconds: Long?,
    val elevationGainMeters: Double?,
    val averageHeartRate: Double?,
    val maxHeartRate: Int?,
)

/**
 * Reads `activities.csv` from a Strava bulk export.
 *
 * Columns are found by their header name, never by position: Strava adds columns over the years, and
 * some names appear twice. The first "Distance" is in the athlete's display unit (kilometres or miles)
 * and the second, in the detailed block that newer exports add, is in metres; the same block repeats
 * "Elapsed Time" and "Max Heart Rate". For a repeated name the last column wins. Missing columns read as
 * unknown values. Rows without an Activity ID are skipped.
 */
public object StravaActivitiesCsv {
    /** Parses every row of [reader]. */
    public fun parse(reader: Reader): List<StravaActivityRow> {
        val csv = CsvReader(reader)
        val header = csv.next()?.map { it.trim() } ?: return emptyList()
        val columns = Columns(header)
        val rows = ArrayList<StravaActivityRow>()
        while (true) {
            val record = csv.next() ?: break
            columns.row(record)?.let(rows::add)
        }
        return rows
    }

    private class Columns(header: List<String>) {
        private val byName: Map<String, List<Int>> = header.withIndex()
            .groupBy({ normalize(it.value) }, { it.index })

        private val idCol = last("Activity ID")
        private val dateCol = last("Activity Date")
        private val nameCol = last("Activity Name")
        private val typeCol = last("Activity Type")
        private val descriptionCol = last("Activity Description")
        private val fileNameCol = last("Filename")
        private val mediaCol = last("Media")
        private val distanceCols = byName[normalize("Distance")].orEmpty()
        private val elapsedCol = last("Elapsed Time")
        private val movingCol = last("Moving Time")
        private val elevationGainCol = last("Elevation Gain")
        private val averageHeartRateCol = last("Average Heart Rate")
        private val maxHeartRateCol = last("Max Heart Rate")

        private fun last(name: String): Int? = byName[normalize(name)]?.last()

        fun row(record: List<String>): StravaActivityRow? {
            fun at(i: Int?): String? = i?.let(record::getOrNull)?.trim()?.ifEmpty { null }
            val id = at(idCol) ?: return null
            val typeName = at(typeCol).orEmpty()
            return StravaActivityRow(
                id = id,
                start = at(dateCol)?.let(::parseDate),
                name = at(nameCol).orEmpty(),
                typeName = typeName,
                sport = StravaSports.fromStravaType(typeName),
                description = at(descriptionCol).orEmpty(),
                fileName = at(fileNameCol),
                media = at(mediaCol)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                distanceMeters = distanceMeters(record),
                elapsedSeconds = at(elapsedCol)?.let(::number)?.toLong(),
                movingSeconds = at(movingCol)?.let(::number)?.toLong(),
                elevationGainMeters = at(elevationGainCol)?.let(::number),
                averageHeartRate = at(averageHeartRateCol)?.let(::number)?.takeIf { it > 0 },
                maxHeartRate = at(maxHeartRateCol)?.let(::number)?.toInt()?.takeIf { it > 0 },
            )
        }

        /** Metres from the detailed column when there are two "Distance" columns, else kilometres from the one. */
        private fun distanceMeters(record: List<String>): Double? {
            fun value(i: Int) = record.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let(::number)
            return when (distanceCols.size) {
                0 -> null
                1 -> value(distanceCols[0])?.times(METERS_PER_KM)
                else -> value(distanceCols.last()) ?: value(distanceCols.first())?.times(METERS_PER_KM)
            }
        }
    }

    private const val METERS_PER_KM = 1000.0

    private fun normalize(name: String) = name.trim().lowercase(Locale.ROOT)

    /** A plain number; Strava writes them with a dot and without grouping, but tolerates "1,234.5". */
    private fun number(text: String): Double? = text.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    private val ENGLISH: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("MMM d, yyyy, h:mm:ss a")
        .toFormatter(Locale.ENGLISH)

    private val ISO_SPACE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    /**
     * "Mar 15, 2021, 7:12:45 AM" (current exports, UTC) or "2021-03-15 07:12:45" (older ones), also with
     * the narrow no-break space newer Java versions put before AM/PM, or an ISO instant.
     */
    internal fun parseDate(text: String): Instant? {
        val cleaned = text.replace(' ', ' ').replace(' ', ' ').trim()
        for (format in listOf(ENGLISH, ISO_SPACE)) {
            try {
                return LocalDateTime.parse(cleaned, format).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                // try the next format
            }
        }
        return try {
            Instant.parse(cleaned)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
