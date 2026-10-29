// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.share

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Saves images into the shared gallery through MediaStore, and copies files to locations the user picked
 * with the Storage Access Framework.
 *
 * Android 10+ needs no permission (scoped storage); Android 8–9 needs
 * [Manifest.permission.WRITE_EXTERNAL_STORAGE], which the manifest declares with `maxSdkVersion=28`.
 */
public class GallerySaver @Inject constructor(@ApplicationContext private val context: Context) {

    /** True when [saveImage] needs a runtime permission that has not been granted yet. */
    public fun needsPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /**
     * Saves [bytes] as an image named [displayName] in `Pictures/[ALBUM]` and returns its content URI.
     * An entry is never left half-written: on failure it is removed and an [IOException] is thrown.
     */
    public suspend fun saveImage(bytes: ByteArray, displayName: String, mimeType: String = "image/png"): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("The gallery refused the new image")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Cannot write to $uri")
                out.use { it.write(bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw if (e is IOException) e else IOException("Saving the image failed", e)
            }
            uri
        }

    /**
     * Streams [source] (an export file) to [destination], a document URI returned by
     * `ACTION_CREATE_DOCUMENT`. Reading and writing both happen on the IO dispatcher.
     */
    public suspend fun copyToDocument(source: java.io.File, destination: Uri): Unit = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Cannot write to $destination")
        out.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    }

    /** Writes [bytes] to [destination], a document URI returned by `ACTION_CREATE_DOCUMENT`. */
    public suspend fun writeToDocument(destination: Uri, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Cannot write to $destination")
        out.use { it.write(bytes) }
    }

    public companion object {
        /** Album (sub-folder of Pictures) that saved images go to. */
        public const val ALBUM: String = "OpenActivity"
    }
}
