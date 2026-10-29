// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import org.openactivity.model.SportType
import java.time.Instant

/** File formats the importer understands. Any of them may also arrive gzip-compressed (`.gpx.gz`). */
public enum class ImportFormat(public val extension: String) {
    GPX("gpx"),
    TCX("tcx"),
    FIT("fit"),
}

/**
 * One sample read from a file. Units are SI: degrees, metres, m/s, bpm, rpm or steps/min, watts, °C.
 *
 * @property distance cumulative distance written by the device, when present.
 */
public data class ImportedPoint(
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double? = null,
    val time: Instant? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val speed: Double? = null,
    val distance: Double? = null,
    val temperature: Double? = null,
) {
    /** True when this sample has a position. */
    val hasPosition: Boolean get() = latitude != null && longitude != null
}

/**
 * An activity read from a file, independent of how the app stores activities.
 *
 * @property sourceName display name of the file it came from.
 * @property format the file's format.
 * @property title name stored in the file, if any; the app picks a default otherwise.
 * @property sport sport declared by the file, or [SportType.OTHER] when it declares none.
 * @property sportDeclared false when [sport] is a guess because the file did not say.
 * @property startTime first timestamp (or the file's declared start), `null` for time-less routes.
 * @property points samples in file order; segments and laps are flattened.
 * @property distanceMeters device distance when the file has it, otherwise measured along the points.
 * @property elapsedSeconds time from first to last sample, when the samples have times.
 * @property deviceName recording device or app named by the file.
 * @property dedupeKey identifies the same recording across formats and re-imports: two files of one
 *   activity (say, the watch's FIT and an exported GPX) share it. Compare with stored activities.
 * @property contentHash SHA-256 of the file's (decompressed) bytes, for byte-identical duplicates.
 * @property truncated true when the file broke off and only the readable part was imported.
 */
public data class ImportedActivity(
    val sourceName: String,
    val format: ImportFormat,
    val title: String?,
    val sport: SportType,
    val sportDeclared: Boolean,
    val startTime: Instant?,
    val points: List<ImportedPoint>,
    val distanceMeters: Double,
    val elapsedSeconds: Long?,
    val deviceName: String?,
    val dedupeKey: String,
    val contentHash: String,
    val truncated: Boolean = false,
) {
    /** True when at least two samples have a position. */
    val hasRoute: Boolean get() = points.count { it.hasPosition } >= 2
}

/** Why a file could not be imported. */
public enum class ImportErrorKind {
    /** Not GPX, TCX or FIT. */
    UNSUPPORTED_FORMAT,

    /** Recognised but not readable (corrupt or not well-formed). */
    MALFORMED,

    /** Readable but contains no activity samples. */
    EMPTY,

    /** Larger than [ActivityFileParser.MAX_FILE_BYTES]. */
    TOO_LARGE,

    /** The file could not be opened or read (permission revoked, provider error). */
    READ_FAILED,
}

/** A per-file import error with a short human-readable [message]. */
public data class ImportError(val kind: ImportErrorKind, val message: String)

/** Result of importing one picked file. */
public sealed interface FileImportResult {
    /** Display name of the picked file. */
    public val displayName: String

    /** The file was read; it may hold several activities (multi-track GPX, multi-activity TCX). */
    public data class Success(override val displayName: String, val activities: List<ImportedActivity>) : FileImportResult

    /** The file could not be imported. Other files of the same batch are unaffected. */
    public data class Failure(override val displayName: String, val error: ImportError) : FileImportResult
}

/** Thrown by [ActivityFileParser] with the [error] to report for the file. */
public class ImportException(public val error: ImportError, cause: Throwable? = null) : Exception(error.message, cause)
