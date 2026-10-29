// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.core.export.ActivityExporter
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.core.export.share.GallerySaver
import org.openactivity.feature.shareexport.ExportActivityProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Track-data formats the detail screen offers, and why a format cannot be written for an activity. */
data class FileFormatOption(val format: ExportFormat, val unavailable: FormatUnavailableReason?)

/**
 * Writes an activity as a GPX, TCX, FIT or GeoJSON file for "Export file" on the detail screen, and
 * copies it to a document the user picked. Files are the owner's own full-resolution data, so no
 * privacy trimming is applied (the share screen offers trimming for files meant for other people).
 */
@Singleton
class ActivityFileExporter internal constructor(
    private val provider: ExportActivityProvider,
    private val exporter: ActivityExporter,
    private val gallery: GallerySaver,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        provider: ExportActivityProvider,
        gallery: GallerySaver,
    ) : this(provider, ActivityExporter.create(context), gallery)

    /** The data formats with their availability for [activityId], or null when it does not exist. */
    suspend fun options(activityId: Long): List<FileFormatOption>? {
        val activity = provider.load(activityId.toString()) ?: return null
        return ExportFormat.DATA.map { FileFormatOption(it, exporter.dataUnavailableReason(activity, it, privacyTrim = false)) }
    }

    /**
     * Writes [activityId] as [format] into the share cache.
     *
     * @throws IllegalStateException when the activity no longer exists.
     * @throws IllegalArgumentException when the activity lacks what the format needs.
     */
    suspend fun export(activityId: Long, format: ExportFormat): ExportedFile {
        require(format.isData) { "$format is not a data format" }
        val activity = checkNotNull(provider.load(activityId.toString())) { "The activity no longer exists" }
        return exporter.exportData(activity, format, privacyTrim = false)
    }

    /** Copies [file] into the document at [destination] (from a create-document picker). */
    suspend fun writeTo(file: ExportedFile, destination: Uri) {
        gallery.copyToDocument(file.file, destination)
    }
}
