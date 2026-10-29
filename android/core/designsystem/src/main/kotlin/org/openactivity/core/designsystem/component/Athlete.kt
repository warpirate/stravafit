// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import org.openactivity.core.designsystem.theme.PillShape
import java.io.File

/**
 * The athlete on this device, as feature screens show them in author rows (activity detail, share).
 *
 * @property name display name ("You" until one is set).
 * @property location free-text place, blank when not shared.
 * @property photoPath absolute path of the profile photo, or null for the initials avatar.
 * @property device the phone activities are recorded on ("Pixel 8"), blank when unknown.
 */
@Immutable
data class AthleteIdentity(
    val name: String = "You",
    val location: String = "",
    val photoPath: String? = null,
    val device: String = "",
)

/** Provided by the app shell from the local profile; defaults to an anonymous athlete. */
val LocalAthlete = staticCompositionLocalOf { AthleteIdentity() }

/** The athlete's photo, or the initials [Avatar] while it loads, without one, or when it cannot be read. */
@Composable
fun AthleteAvatar(athlete: AthleteIdentity, modifier: Modifier = Modifier, size: Dp = AvatarSize.m) {
    var photo by remember(athlete.photoPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(athlete.photoPath) {
        photo = athlete.photoPath?.let { path ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(File(path).absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    val bitmap = photo
    if (bitmap == null) {
        Avatar(athlete.name, modifier, size)
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(PillShape).semantics { contentDescription = athlete.name },
        )
    }
}
