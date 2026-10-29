// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.TrackFileEncoder
import java.io.IOException
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Outcome of a bulk export.
 *
 * @property exported activities written as GPX files.
 * @property skipped activities without a GPS track (indoor sessions), which GPX cannot hold.
 */
data class ArchiveSummary(val exported: Int, val skipped: Int)

/** Exports every activity on the device (settings 1.5: "Download your data", immediately, no email). */
interface ActivityArchiveExporter {
    /**
     * Writes a ZIP with one GPX file per activity to the document at [destination].
     *
     * @throws IOException when the document cannot be written.
     */
    suspend fun exportAll(destination: Uri): ArchiveSummary
}

/** [ActivityArchiveExporter] writing to a Storage Access Framework document. */
class GpxZipArchiveExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: GpxZipWriter,
) : ActivityArchiveExporter {
    override suspend fun exportAll(destination: Uri): ArchiveSummary = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Cannot open the chosen file")
        output.use { writer.write(it) }
    }
}

/** Streams every stored activity into a ZIP of GPX files, one activity in memory at a time. */
class GpxZipWriter @Inject constructor(
    private val activities: ActivityRepository,
    private val encoder: TrackFileEncoder,
) {
    /** Writes the archive to [output] (left open for the caller to close). */
    suspend fun write(output: OutputStream): ArchiveSummary {
        val all = activities.observeActivities().first()
        var exported = 0
        var skipped = 0
        val names = HashSet<String>()
        val zip = ZipOutputStream(output)
        for (summary in all) {
            coroutineContext.ensureActive()
            val detail = activities.observeActivityDetail(summary.id).first() ?: continue
            val export = detail.toExportActivity()
            if (encoder.unavailableReason(export, ExportFormat.GPX) != null) {
                skipped++
                continue
            }
            val bytes = encoder.encode(export, ExportFormat.GPX)
            zip.putNextEntry(ZipEntry(uniqueName(entryName(summary), names)))
            zip.write(bytes)
            zip.closeEntry()
            exported++
        }
        zip.finish()
        zip.flush()
        return ArchiveSummary(exported, skipped)
    }

    internal companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** "2026-10-27-Morning_Run.gpx", from the local start date and the title. */
        fun entryName(activity: SavedActivity): String {
            val date = DATE.format(activity.startTime.atZone(activity.timeZone))
            return "$date-${ExportFileStore.fileNameStem(activity.title)}.gpx"
        }

        /** Appends "-2", "-3"... when two activities share a date and title. */
        fun uniqueName(name: String, taken: MutableSet<String>): String {
            if (taken.add(name)) return name
            val stem = name.removeSuffix(".gpx")
            var n = 2
            while (!taken.add("$stem-$n.gpx")) n++
            return "$stem-$n.gpx"
        }
    }
}
