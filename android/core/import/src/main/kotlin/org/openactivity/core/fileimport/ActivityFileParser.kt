// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import app.stravafit.lib.fit.FitActivityDecoder
import app.stravafit.lib.fit.protocol.FitFormatException
import app.stravafit.lib.gpxtcx.GpxReader
import app.stravafit.lib.gpxtcx.TcxReader
import app.stravafit.lib.gpxtcx.TrackFileFormatException
import app.stravafit.lib.gpxtcx.TrackPoint
import org.openactivity.geo.Spherical
import org.openactivity.model.SportType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Parses GPX, TCX and FIT files (plain or gzip-compressed) into [ImportedActivity] values.
 *
 * The format is detected from the content (FIT header, XML root element), with the file-name
 * extension only as a tie-breaker, because document providers often report a generic MIME type.
 * Pure JVM code: it never touches Android APIs, so it is easy to test and to reuse.
 */
public class ActivityFileParser @Inject constructor() {

    /**
     * Reads every activity in [input] (named [fileName]). At most [MAX_FILE_BYTES] are read.
     *
     * @throws ImportException with the reason the file cannot be imported.
     */
    public fun parse(fileName: String, input: InputStream): List<ImportedActivity> {
        var bytes = readLimited(input)
        var name = fileName
        if (isGzip(bytes)) {
            bytes = try {
                readLimited(GZIPInputStream(ByteArrayInputStream(bytes)))
            } catch (e: IOException) {
                throw ImportException(ImportError(ImportErrorKind.MALFORMED, "The compressed file is damaged"), e)
            }
            name = name.removeSuffix(".gz").removeSuffix(".GZ")
        }
        if (bytes.isEmpty()) throw ImportException(ImportError(ImportErrorKind.EMPTY, "The file is empty"))
        val format = detectFormat(name, bytes)
            ?: throw ImportException(ImportError(ImportErrorKind.UNSUPPORTED_FORMAT, "Not a GPX, TCX or FIT file"))
        val hash = sha256(bytes)
        val activities = try {
            when (format) {
                ImportFormat.GPX -> parseGpx(fileName, bytes, hash)
                ImportFormat.TCX -> parseTcx(fileName, bytes, hash)
                ImportFormat.FIT -> parseFit(fileName, bytes, hash)
            }
        } catch (e: TrackFileFormatException) {
            throw ImportException(ImportError(ImportErrorKind.MALFORMED, "The ${format.name} file is damaged: ${e.message}"), e)
        } catch (e: FitFormatException) {
            throw ImportException(ImportError(ImportErrorKind.MALFORMED, "The FIT file is damaged: ${e.message}"), e)
        }
        val usable = activities.filter { it.points.isNotEmpty() }
        if (usable.isEmpty()) throw ImportException(ImportError(ImportErrorKind.EMPTY, "The file contains no recorded samples"))
        return usable
    }

    private fun parseGpx(fileName: String, bytes: ByteArray, hash: String): List<ImportedActivity> {
        val doc = GpxReader(recoverPartial = true).read(ByteArrayInputStream(bytes))
        val fromTracks = doc.tracks.map { track ->
            val points = track.segments.flatMap { it.points }.map { it.toImported() }
            val (sport, declared) = Sports.fromGpxType(track.type)
            build(fileName, ImportFormat.GPX, track.name ?: doc.metadata.name, sport, declared, null, points, doc.creator, hash, doc.truncated)
        }
        // Planned routes carry no recording; only use them when the file has no track at all.
        val fromRoutes = if (fromTracks.any { it.points.isNotEmpty() }) emptyList() else doc.routes.map { route ->
            build(fileName, ImportFormat.GPX, route.name ?: doc.metadata.name, SportType.OTHER, false, null, route.points.map { it.toImported() }, doc.creator, hash, doc.truncated)
        }
        return fromTracks + fromRoutes
    }

    private fun parseTcx(fileName: String, bytes: ByteArray, hash: String): List<ImportedActivity> {
        val doc = TcxReader().read(ByteArrayInputStream(bytes))
        return doc.activities.map { activity ->
            val points = activity.laps.flatMap { it.trackpoints }.map { it.toImported() }
            val (sport, declared) = Sports.fromTcx(activity.sport)
            val lapDistance = activity.laps.sumOf { it.distanceMeters }.takeIf { it > 0 }
            build(
                fileName, ImportFormat.TCX, activity.notes?.lineSequence()?.firstOrNull()?.take(MAX_TITLE),
                sport, declared, activity.id, points, activity.creatorName, hash, false, lapDistance,
            )
        }
    }

    private fun parseFit(fileName: String, bytes: ByteArray, hash: String): List<ImportedActivity> {
        val fit = FitActivityDecoder().decode(bytes)
        val points = fit.records.map { r ->
            ImportedPoint(
                latitude = r.latitude, longitude = r.longitude, elevation = r.altitude, time = r.timestamp,
                heartRate = r.heartRate, cadence = r.cadence, power = r.power, speed = r.speed, distance = r.distance,
                temperature = r.temperature?.toDouble(),
            )
        }
        val session = fit.sessions.firstOrNull()
        val (sport, declared) = Sports.fromFit(session?.sport ?: fit.laps.firstNotNullOfOrNull { it.sport })
        val start = session?.startTime ?: points.firstNotNullOfOrNull { it.time } ?: fit.fileId.timeCreated
        val distance = fit.sessions.mapNotNull { it.totalDistance }.takeIf { it.isNotEmpty() }?.sum()
        return listOf(build(fileName, ImportFormat.FIT, null, sport, declared, start, points, fit.fileId.productName, hash, false, distance))
    }

    @Suppress("LongParameterList")
    private fun build(
        fileName: String,
        format: ImportFormat,
        title: String?,
        sport: SportType,
        sportDeclared: Boolean,
        declaredStart: Instant?,
        points: List<ImportedPoint>,
        device: String?,
        hash: String,
        truncated: Boolean,
        declaredDistance: Double? = null,
    ): ImportedActivity {
        val firstTime = points.firstNotNullOfOrNull { it.time }
        val lastTime = points.asReversed().firstNotNullOfOrNull { it.time }
        val start = declaredStart ?: firstTime
        val elapsed = if (start != null && lastTime != null) Duration.between(start, lastTime).seconds.coerceAtLeast(0) else null
        val distance = declaredDistance
            ?: points.asReversed().firstNotNullOfOrNull { it.distance }
            ?: measuredDistance(points)
        return ImportedActivity(
            sourceName = fileName,
            format = format,
            title = title?.trim()?.ifEmpty { null },
            sport = sport,
            sportDeclared = sportDeclared,
            startTime = start,
            points = points,
            distanceMeters = distance,
            elapsedSeconds = elapsed,
            deviceName = device?.trim()?.ifEmpty { null },
            dedupeKey = dedupeKey(start, points, hash),
            contentHash = hash,
            truncated = truncated,
        )
    }

    public companion object {
        /** Largest file (after decompression) the importer reads: far above any real activity. */
        public const val MAX_FILE_BYTES: Int = 64 * 1024 * 1024

        private const val MAX_TITLE = 120

        /**
         * Key shared by every file of one recording: the time and place of the first sample that has both,
         * with the position rounded to about 10 m. Formats round positions and times differently, so
         * neither the content hash nor point counts can match a FIT against its GPX export; time and place
         * can. The first *positioned* sample is used rather than the declared start because watches log
         * samples (and set the session start) before the GPS fix, and most converters drop those
         * position-less samples from GPX. Indoor files use [start]; files without any time fall back to
         * the content hash.
         */
        public fun dedupeKey(start: Instant?, points: List<ImportedPoint>, contentHash: String): String {
            val anchor = points.firstOrNull { it.hasPosition && it.time != null }
            val anchorTime = anchor?.time ?: start ?: points.firstNotNullOfOrNull { it.time }
                ?: return "content:$contentHash"
            val place = anchor?.let { String.format(Locale.ROOT, "%.4f,%.4f", it.latitude, it.longitude) } ?: "indoor"
            return "v1:" + sha256("${anchorTime.epochSecond}|$place".toByteArray(Charsets.UTF_8)).take(32)
        }

        internal fun detectFormat(fileName: String, bytes: ByteArray): ImportFormat? {
            if (bytes.size >= 12 && (bytes[0].toInt() == 12 || bytes[0].toInt() == 14) &&
                bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
                bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
            ) {
                return ImportFormat.FIT
            }
            val head = sniffText(bytes)
            ROOT_ELEMENT.find(head)?.groupValues?.get(1)?.let { root ->
                return when (root) {
                    "gpx" -> ImportFormat.GPX
                    "TrainingCenterDatabase" -> ImportFormat.TCX
                    else -> null
                }
            }
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            // Unrecognisable content with a track-file extension is reported as a damaged file of that type.
            return ImportFormat.entries.firstOrNull { it.extension == ext }
        }

        private val ROOT_ELEMENT = Regex("""<(?:[A-Za-z_][\w.-]*:)?(gpx|TrainingCenterDatabase|[A-Za-z_][\w.-]*)[\s>/]""")

        /** Decodes the first kilobytes as text, skipping the declaration, comments and doctype. */
        private fun sniffText(bytes: ByteArray): String {
            val n = minOf(bytes.size, 4096)
            val utf16 = n >= 2 && ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) || (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))
            val text = if (utf16) String(bytes, 0, n, Charsets.UTF_16) else String(bytes, 0, n, Charsets.ISO_8859_1)
            return text.replace(Regex("""<\?.*?\?>|<!--.*?-->|<!DOCTYPE[^>]*>""", RegexOption.DOT_MATCHES_ALL), "")
        }

        private fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()

        private fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) {
                    throw ImportException(ImportError(ImportErrorKind.TOO_LARGE, "The file is larger than ${MAX_FILE_BYTES / (1024 * 1024)} MB"))
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        internal fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun measuredDistance(points: List<ImportedPoint>): Double {
            var total = 0.0
            var previous: ImportedPoint? = null
            for (p in points) {
                if (!p.hasPosition) continue
                previous?.let { total += Spherical.distance(it.latitude!!, it.longitude!!, p.latitude!!, p.longitude!!) }
                previous = p
            }
            return total
        }

        private fun TrackPoint.toImported() = ImportedPoint(
            latitude = latitude, longitude = longitude, elevation = elevation, time = time, heartRate = heartRate,
            cadence = cadence, power = power, speed = speed, distance = distance, temperature = temperature,
        )
    }
}
