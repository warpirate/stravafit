// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.openactivity.core.designsystem.theme.Ref
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.overlay.MapPalette
import org.openactivity.core.map.style.ConfigurableMapStyleProvider
import org.openactivity.core.map.style.MapStyleConfig
import org.openactivity.core.map.style.MapStyleProvider

/**
 * The [MapStyleProvider] maps in this subtree use. Defaults to the keyless OpenFreeMap setup; the
 * app provides its Hilt-bound instance at the root so a self-hosted configuration applies everywhere.
 */
val LocalMapStyleProvider = compositionLocalOf<MapStyleProvider> {
    ConfigurableMapStyleProvider(MapStyleConfig.OpenFreeMap)
}

/** True when the current Material theme is dark, judged from the background colour. */
@Composable
@ReadOnlyComposable
fun isMapThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Overlay colours from the Ridgeline theme: the user's own lines in the route colour, planned
 * routes in the info blue so the two never look alike, and a casing that matches the theme.
 */
@Composable
@ReadOnlyComposable
fun mapPalette(): MapPalette {
    val colors = RidgelineTheme.colors
    val dark = isMapThemeDark()
    return MapPalette(
        liveTrack = colors.routeSelf.toArgb(),
        plannedRoute = (if (dark) Ref.InfoDark else Ref.InfoLight).toArgb(),
        activityRoute = colors.routeSelf.toArgb(),
        casing = colors.routeCasing.toArgb(),
        marker = colors.routeSelf.toArgb(),
        puck = (if (dark) Ref.Primary300 else Ref.Primary500).toArgb(),
        puckAccuracy = (if (dark) Ref.Primary300 else Ref.Primary500).toArgb(),
        land = colors.mapLand.toArgb(),
    )
}
