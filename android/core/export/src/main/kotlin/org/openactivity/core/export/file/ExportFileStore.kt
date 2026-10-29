// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.core.export.share.ExportFileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file ready to be shared or saved.
 *
 * @property file the file in the app's export cache.
 * @property uri a `content://` URI for it, served by [ExportFileProvider].
 * @property displayName the name receiving apps see.
 * @property mimeType the file's MIME type.
 */
public data class ExportedFile(
    val file: File,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)

/**
 * Writes export files into `cache/exports/`, the only directory the [ExportFileProvider] exposes.
 *
 * Files are short-lived: anything older than [MAX_AGE_MILLIS] is deleted on the next write, and the
 * system may clear the cache at any time, so files are always written fresh right before sharing.
 */
@Singleton
public class ExportFileStore internal constructor(
    private val context: Context,
    private val uriFor: (Context, File) -> Uri,
) {
    @Inject
    public constructor(@ApplicationContext context: Context) : this(context, ExportFileProvider::uriFor)

    private val directory: File get() = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    /**
     * Writes a file named after [baseName] with [format]'s extension, streaming its content through
     * [write]. A partially written file is deleted when [write] throws.
     */
    @Throws(IOException::class)
    public fun write(baseName: String, format: ExportFormat, write: (OutputStream) -> Unit): ExportedFile =
        write(baseName, format.extension, format.mimeType, write)

    /** Like the [ExportFormat] overload, for an explicit [extension] and [mimeType]. */
    @Throws(IOException::class)
    public fun write(baseName: String, extension: String, mimeType: String, write: (OutputStream) -> Unit): ExportedFile {
        pruneOldFiles()
        val name = "${fileNameStem(baseName)}.$extension"
        val target = File(directory, name)
        val temp = File(directory, "$name.part")
        try {
            temp.outputStream().buffered().use(write)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IOException("Could not move $temp to $target")
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        return ExportedFile(target, uriFor(context, target), name, mimeType)
    }

    /** Deletes every export file. */
    public fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun pruneOldFiles() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    public companion object {
        /** Sub-directory of the cache that holds export files. Must match `res/xml/export_file_paths.xml`. */
        public const val DIRECTORY: String = "exports"

        /** Export files older than this are removed. */
        public const val MAX_AGE_MILLIS: Long = 24L * 60 * 60 * 1000

        private const val MAX_STEM_LENGTH = 60

        /**
         * Turns a title into a portable file-name stem: accents removed, anything outside letters,
         * digits, dot, dash and underscore replaced by `_`, never empty and at most 60 characters.
         */
        public fun fileNameStem(title: String): String {
            val ascii = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val cleaned = ascii.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.').take(MAX_STEM_LENGTH).trimEnd('_', '.')
            return cleaned.ifEmpty { "activity" }
        }
    }
}
