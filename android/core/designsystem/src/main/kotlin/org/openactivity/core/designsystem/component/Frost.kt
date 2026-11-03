// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.RidgelineTheme

/**
 * Frosted surfaces, after Telegram's: a bar or control floating over content shows that content blurred
 * and tinted through it. A screen marks what may be seen through with [frostSource] and provides the
 * state in [LocalFrost]; [Modifier.frosted] and [FrostedSurface] then frost against it. Without a
 * provider (previews, screens that never adopted it) they fall back to the plain solid surface.
 *
 * Blur needs Android 12+; below that, and in battery saver, the tint alone is drawn at a higher opacity.
 */
val LocalFrost = staticCompositionLocalOf<HazeState?> { null }

/** Holds the content a frosted surface blurs; remember one per screen. */
@Composable
fun rememberFrost(): HazeState = remember { HazeState() }

/** Marks this content as what frosted surfaces on top of it show through. */
fun Modifier.frostSource(state: HazeState?): Modifier = if (state == null) this else hazeSource(state)

/**
 * Frosts this element against [state] (default: [LocalFrost]) with [color] as the tint, clipped to
 * [shape]. Draws [color] solid when there is nothing to frost against.
 */
@Composable
fun Modifier.frosted(
    shape: Shape,
    color: Color = MaterialTheme.colorScheme.surface,
    state: HazeState? = LocalFrost.current,
): Modifier {
    if (state == null) return background(color, shape)
    val blur = !isPowerSaving()
    return clip(shape).hazeEffect(
        state,
        HazeStyle(
            backgroundColor = color,
            tint = HazeTint(color.copy(alpha = if (blur) FROST_ALPHA else FROST_FALLBACK_ALPHA)),
            blurRadius = FROST_BLUR,
            noiseFactor = 0f,
            fallbackTint = HazeTint(color.copy(alpha = FROST_FALLBACK_ALPHA)),
        ),
    ) { blurEnabled = blurEnabled && blur }
}

/**
 * A control floating over a map or content: frosted with a hairline edge against [frost] (default
 * [LocalFrost]), otherwise the solid `float` surface with the float shadow (the strava look).
 */
@Composable
fun FrostedSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = RidgelineTheme.colors.float,
    contentColor: Color = RidgelineTheme.colors.onFloat,
    frost: HazeState? = LocalFrost.current,
    content: @Composable () -> Unit,
) {
    val surfaceModifier = if (frost == null) modifier else modifier.frosted(shape, color, frost)
    val fill = if (frost == null) color else Color.Transparent
    val shadow = if (frost == null) Elevation.float else 0.dp
    val border = if (frost == null) null else BorderStroke(Dp.Hairline, RidgelineTheme.colors.hairline)
    if (onClick != null) {
        Surface(onClick, surfaceModifier, enabled, shape, fill, contentColor, shadowElevation = shadow, border = border, content = content)
    } else {
        Surface(surfaceModifier, shape, fill, contentColor, shadowElevation = shadow, border = border, content = content)
    }
}

/**
 * A [Scaffold] whose content scrolls up behind a frosted top bar. [content] fills the whole screen and
 * gets the bar height as padding: lists add it to their content padding, anything that must not sit
 * under the bar pads by it. The content itself does not see [LocalFrost], so controls inside it (a map's
 * buttons) never frost against the content they are part of.
 */
@Composable
fun FrostedScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    val frost = rememberFrost()
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0),
        topBar = { CompositionLocalProvider(LocalFrost provides frost) { topBar() } },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(containerColor).frostSource(frost)) {
            CompositionLocalProvider(LocalFrost provides null) { content(padding) }
        }
    }
}

@Composable
private fun isPowerSaving(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }?.isPowerSaveMode == true
}

// Telegram tints its blurred bars at 85 % (76 % without liquid glass); 80 % keeps text on them crisp
// while the content behind still shows through.
private val FROST_BLUR = 20.dp
private const val FROST_ALPHA = 0.80f
private const val FROST_FALLBACK_ALPHA = 0.94f
