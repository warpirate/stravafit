// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Keeps the profile photo in private app storage. */
interface ProfilePhotoStore {
    /**
     * Decodes [source], centre-crops it to a square of at most [MAX_SIZE_PX] and saves it as JPEG.
     *
     * @return the absolute path of the saved file.
     * @throws IOException when the image cannot be read or decoded.
     */
    suspend fun save(source: Uri): String

    /** Deletes a photo previously returned by [save]; unknown paths are ignored. */
    suspend fun delete(path: String)

    companion object {
        const val MAX_SIZE_PX: Int = 512
    }
}

/**
 * [ProfilePhotoStore] in `files/profile/`. Each save gets a new file name so image caches never
 * show a stale photo. The original picked file is never kept, so no EXIF location data survives.
 */
@Singleton
class FileProfilePhotoStore @Inject constructor(@ApplicationContext private val context: Context) : ProfilePhotoStore {

    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    override suspend fun save(source: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open the picked image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, ProfilePhotoStore.MAX_SIZE_PX)
        }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("The image could not be decoded")
        val square = centreSquare(decoded, ProfilePhotoStore.MAX_SIZE_PX)
        val file = File(directory, "avatar-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            if (!square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Could not save the photo")
        }
        if (square !== decoded) square.recycle()
        decoded.recycle()
        file.absolutePath
    }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            // Only ever delete our own files.
            if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
        }
    }

    internal companion object {
        private const val DIRECTORY = "profile"
        private const val JPEG_QUALITY = 90

        /** Largest power-of-two sample size that keeps the short side at or above [target]. */
        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (min(width, height) / (sample * 2) >= target) sample *= 2
            return sample
        }

        private fun centreSquare(bitmap: Bitmap, maxSize: Int): Bitmap {
            val side = min(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            if (side <= maxSize) return cropped
            val scaled = Bitmap.createScaledBitmap(cropped, maxSize, maxSize, true)
            if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
            return scaled
        }
    }
}
