// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.activityextras.ActivityPhoto

/**
 * Shows [photo] cropped to fill the box. The file is decoded off the main thread and subsampled to
 * about [targetSize], so a strip of thumbnails stays cheap. [placeholder] fills the box while loading.
 */
@Composable
public fun PhotoThumbnail(
    photo: ActivityPhoto,
    contentDescription: String?,
    targetSize: Dp,
    placeholder: Color,
    modifier: Modifier = Modifier,
) {
    val targetPx = with(LocalDensity.current) { targetSize.roundToPx() }
    var bitmap by remember(photo.file, targetPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.file, targetPx) {
        bitmap = withContext(Dispatchers.IO) { decodeSampled(photo, targetPx) }
    }
    Box(modifier.background(placeholder)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Decodes [photo] subsampled so its short side stays at or above [targetPx]. */
internal fun decodeSampled(photo: ActivityPhoto, targetPx: Int): Bitmap? {
    if (!photo.file.isFile) return null
    var sample = 1
    val shortSide = minOf(photo.width, photo.height).coerceAtLeast(1)
    while (shortSide / (sample * 2) >= targetPx.coerceAtLeast(1)) sample *= 2
    return try {
        BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: OutOfMemoryError) {
        null
    }
}
