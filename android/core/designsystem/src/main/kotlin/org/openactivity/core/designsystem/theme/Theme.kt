// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Light Material 3 scheme built from Ridgeline tokens. */
val RidgelineLightScheme: ColorScheme = lightColorScheme(
    primary = Ref.Primary600,
    onPrimary = Ref.Slate0,
    primaryContainer = Ref.Primary100,
    onPrimaryContainer = Ref.Primary800,
    inversePrimary = Ref.Primary200,
    secondary = Ref.Slate600,
    onSecondary = Ref.Slate0,
    secondaryContainer = Ref.Slate100,
    onSecondaryContainer = Ref.Slate800,
    tertiary = Ref.Signal700,
    onTertiary = Ref.Slate900, // dark text on lime; white fails AA
    tertiaryContainer = Ref.Signal500,
    onTertiaryContainer = Ref.Slate850,
    background = Ref.Slate50,
    onBackground = Ref.Slate900,
    surface = Ref.Slate0,
    onSurface = Ref.Slate900,
    surfaceVariant = Ref.Slate25,
    onSurfaceVariant = Ref.Slate600,
    surfaceTint = Ref.Primary600,
    inverseSurface = Ref.Slate800,
    inverseOnSurface = Ref.Slate50,
    error = Ref.ErrorLight,
    onError = Ref.Slate0,
    errorContainer = Ref.ErrorContainerLight,
    onErrorContainer = Ref.OnErrorContainerLight,
    outline = Ref.Slate400,
    outlineVariant = Ref.Slate100,
    scrim = Ref.Slate950,
    surfaceDim = Ref.Slate100,
    surfaceBright = Ref.Slate0,
    surfaceContainerLowest = Ref.Slate0,
    surfaceContainerLow = Ref.Slate25,
    surfaceContainer = Ref.Slate50,
    surfaceContainerHigh = Ref.Slate100,
    surfaceContainerHighest = Ref.Slate200,
)

/** Dark Material 3 scheme built from Ridgeline tokens. */
val RidgelineDarkScheme: ColorScheme = darkColorScheme(
    primary = Ref.Primary300,
    onPrimary = Ref.Primary900,
    primaryContainer = Ref.Primary800,
    onPrimaryContainer = Ref.Primary100,
    inversePrimary = Ref.Primary600,
    secondary = Ref.Slate300,
    onSecondary = Ref.Slate900,
    secondaryContainer = Ref.Slate700,
    onSecondaryContainer = Ref.Slate100,
    tertiary = Ref.Signal300,
    onTertiary = Ref.Slate850,
    tertiaryContainer = Ref.Signal900,
    onTertiaryContainer = Ref.Signal100,
    background = Ref.Slate950,
    onBackground = Ref.Slate50,
    surface = Ref.Slate900,
    onSurface = Ref.Slate50,
    surfaceVariant = Ref.Slate850,
    onSurfaceVariant = Ref.Slate300,
    surfaceTint = Ref.Primary300,
    inverseSurface = Ref.Slate100,
    inverseOnSurface = Ref.Slate900,
    error = Ref.ErrorDark,
    onError = Ref.Slate950,
    errorContainer = Ref.ErrorContainerDark,
    onErrorContainer = Ref.OnErrorContainerDark,
    outline = Ref.Slate500,
    outlineVariant = Ref.Slate800,
    scrim = Ref.Slate950,
    surfaceDim = Ref.Slate950,
    surfaceBright = Ref.Slate700,
    surfaceContainerLowest = Ref.Slate950,
    surfaceContainerLow = Ref.Slate900,
    surfaceContainer = Ref.Slate900,
    surfaceContainerHigh = Ref.Slate850,
    surfaceContainerHighest = Ref.Slate800,
)

/**
 * Root Ridgeline theme.
 *
 * Dynamic colour is intentionally off so the brand and zone colours stay stable and
 * contrast-verified. Motion is reduced when [reduceMotion] is set (in-app switch) or the system
 * animator duration scale is 0.
 *
 * @param darkTheme render the dark palette.
 * @param reduceMotion the in-app reduced-motion preference.
 */
@Composable
fun RidgelineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemReduced = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalRidgelineColors provides if (darkTheme) DarkRidgelineColors else LightRidgelineColors,
        LocalRidgelineTextStyles provides RidgelineTextStyles(),
        LocalRidgelineMotion provides RidgelineMotion(reduced = reduceMotion || systemReduced),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) RidgelineDarkScheme else RidgelineLightScheme,
            typography = RidgelineTypography,
            shapes = RidgelineShapes,
            content = content,
        )
    }
}

/** Accessors for Ridgeline values outside the Material 3 theme, e.g. `RidgelineTheme.colors.signal`. */
object RidgelineTheme {
    /** Extra colour roles for the current light or dark mode. */
    val colors: RidgelineColors
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineColors.current

    /** Stat, caption and overline text styles. */
    val text: RidgelineTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineTextStyles.current

    /** Motion durations, already adjusted for reduced motion. */
    val motion: RidgelineMotion
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineMotion.current

    /** Spacing scale and layout constants. */
    val spacing: Spacing get() = Spacing
}
