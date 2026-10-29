// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.designsystem.component.Avatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.theme.PillShape
import java.io.File

/**
 * The athlete's photo from local storage, or the initials avatar while it loads, when there is
 * none, or when the file cannot be decoded.
 */
@Composable
fun ProfileAvatar(name: String, photoPath: String?, modifier: Modifier = Modifier, size: Dp = AvatarSize.m) {
    var photo by remember(photoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoPath) {
        photo = photoPath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(File(path).absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val bitmap = photo
    if (bitmap == null) {
        Avatar(name, modifier, size)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(PillShape)
                .semantics { contentDescription = name },
        )
    }
}
