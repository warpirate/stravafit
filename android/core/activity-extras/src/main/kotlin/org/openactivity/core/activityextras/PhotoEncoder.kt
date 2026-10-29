// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Copies a picture into private storage as a JPEG no larger than [maxSide] pixels on its long side.
 *
 * Re-encoding strips every EXIF tag (camera position, device serials); the EXIF orientation is applied
 * to the pixels first so the photo still looks the way it did in the gallery.
 */
internal class PhotoEncoder(
    private val resolver: ContentResolver,
    private val maxSide: Int = DEFAULT_MAX_SIDE,
    private val quality: Int = DEFAULT_QUALITY,
) {
    /** Size of an encoded photo. */
    data class Encoded(val width: Int, val height: Int)

    /**
     * Encodes [source] into [target].
     *
     * @throws IOException when the picture cannot be read or decoded.
     */
    fun encode(source: Uri, target: File): Encoded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not a picture")

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        val decoded = open(source).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unreadable picture")
        val oriented = applyOrientation(decoded, orientation(source))
        val scaled = scaleDown(oriented, maxSide)
        try {
            val dir = target.parentFile ?: throw IOException("No folder for $target")
            dir.mkdirs()
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("Could not encode the picture")
            }
            replaceAtomically(tmp, target)
            return Encoded(scaled.width, scaled.height)
        } finally {
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun open(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw IOException("No data at $uri")

    private fun orientation(uri: Uri): Int = try {
        open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    companion object {
        /** Long side of stored photos: sharp on any phone, small enough to keep storage modest. */
        const val DEFAULT_MAX_SIDE: Int = 2048

        /** JPEG quality of stored photos. */
        const val DEFAULT_QUALITY: Int = 85

        /** Largest power-of-two subsampling that keeps the long side at or above [maxSide]. */
        fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
            return sample
        }

        /** The matrix that turns a picture with EXIF [orientation] upright, or null when it already is. */
        fun orientationMatrix(orientation: Int): Matrix? {
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    m.postRotate(90f)
                    m.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    m.postRotate(270f)
                    m.postScale(-1f, 1f)
                }
                else -> return null
            }
            return m
        }

        private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = orientationMatrix(orientation) ?: return bitmap
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
            val longSide = max(bitmap.width, bitmap.height)
            if (longSide <= maxSide) return bitmap
            val f = maxSide.toFloat() / longSide
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * f).roundToInt().coerceAtLeast(1),
                (bitmap.height * f).roundToInt().coerceAtLeast(1),
                true,
            )
        }
    }
}
