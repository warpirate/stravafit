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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/*
 * Material 3 role mapping for design direction "strava" (ADR-014).
 *
 * primary is the fill orange #FC5200, as in Strava. It is 3.3:1 against white, so in light mode it
 * is for fills, icons and >=19sp/800 labels only. Small orange text (TextButton, links, active nav
 * label) must use RidgelineTheme.colors.accentText (#CC3D00 light / #FF6A2B dark).
 * surfaceTint is transparent: surfaces stay flat, with no tonal elevation overlay.
 */

/** Light Material 3 scheme. */
val RidgelineLightScheme: ColorScheme = lightColorScheme(
    primary = Ref.Accent, // accent
    onPrimary = Ref.White, // on-accent
    primaryContainer = Color(0xFFFFE7DB), // accent-area flattened on white
    onPrimaryContainer = Ref.Ink,
    inversePrimary = Ref.AccentTextDark,
    secondary = Ref.Ink, // pill-inverse: sport chip, active layer chip, Create route
    onSecondary = Ref.White,
    secondaryContainer = Ref.Surface2Light,
    onSecondaryContainer = Ref.Ink,
    tertiary = Ref.ChartLineLight, // chart-line
    onTertiary = Ref.White,
    tertiaryContainer = Color(0xFFE3EDFB),
    onTertiaryContainer = Ref.Ink,
    background = Ref.White, // bg
    onBackground = Ref.Ink, // text
    surface = Ref.White, // surface
    onSurface = Ref.Ink,
    surfaceVariant = Ref.Surface2Light, // surface-2
    onSurfaceVariant = Ref.MutedLight, // muted (5.1:1)
    surfaceTint = Color.Transparent,
    inverseSurface = Ref.Ink,
    inverseOnSurface = Ref.TextDark,
    error = Ref.ErrorLight,
    onError = Ref.White,
    errorContainer = Color(0xFFFCE4E2),
    onErrorContainer = Color(0xFF7A1712),
    outline = Ref.HairStrongLight, // hair-strong: outlined buttons, sport circles
    outlineVariant = Ref.HairLight, // hair: dividers
    scrim = Ref.Black,
    surfaceDim = Ref.GapLight, // gap
    surfaceBright = Ref.White,
    surfaceContainerLowest = Ref.White,
    surfaceContainerLow = Ref.White, // bottom sheets
    surfaceContainer = Ref.White, // NavigationBar
    surfaceContainerHigh = Ref.White, // raised: dialogs
    surfaceContainerHighest = Ref.Surface2Light, // filled cards, wells
)

/** Dark Material 3 scheme. */
val RidgelineDarkScheme: ColorScheme = darkColorScheme(
    primary = Ref.Accent, // 5.3:1 on surface, AA as text too
    onPrimary = Ref.White,
    primaryContainer = Color(0xFF3A1E12), // accent-area flattened on surface
    onPrimaryContainer = Ref.TextDark,
    inversePrimary = Ref.AccentTextLight,
    secondary = Ref.TextDark, // pill-inverse
    onSecondary = Ref.OnPillInverseDark,
    secondaryContainer = Ref.Surface2Dark,
    onSecondaryContainer = Ref.TextDark,
    tertiary = Ref.ChartLineDark,
    onTertiary = Ref.OnPillInverseDark,
    tertiaryContainer = Color(0xFF1C2B44),
    onTertiaryContainer = Ref.TextDark,
    background = Ref.BgDark, // bg
    onBackground = Ref.TextDark,
    surface = Ref.SurfaceDark, // surface: cards, nav, sheets
    onSurface = Ref.TextDark,
    surfaceVariant = Ref.Surface2Dark,
    onSurfaceVariant = Ref.MutedDark,
    surfaceTint = Color.Transparent,
    inverseSurface = Ref.TextDark,
    inverseOnSurface = Ref.Ink,
    error = Ref.ErrorDark,
    onError = Ref.OnPillInverseDark,
    errorContainer = Color(0xFF4A1513),
    onErrorContainer = Color(0xFFFFD9D6),
    outline = Ref.HairStrongDark,
    outlineVariant = Ref.HairDark,
    scrim = Ref.Black,
    surfaceDim = Ref.Black, // gap
    surfaceBright = Ref.RaisedDark,
    surfaceContainerLowest = Ref.BgDark,
    surfaceContainerLow = Ref.SurfaceDark, // bottom sheets
    surfaceContainer = Ref.SurfaceDark, // NavigationBar
    surfaceContainerHigh = Ref.RaisedDark, // raised: dialogs
    surfaceContainerHighest = Ref.RaisedDark,
)

/**
 * Root theme. The name is kept from Ridgeline so every call site compiles unchanged; the values
 * are the strava direction.
 *
 * Dynamic colour stays off so the orange and the contrast-checked roles never shift. Motion is
 * reduced when [reduceMotion] is set (in-app switch) or the system animator duration scale is 0.
 *
 * @param darkTheme render the dark palette (Always dark / Always light / Follow device).
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

/** Accessors for values outside the Material 3 theme, e.g. `RidgelineTheme.colors.accentText`. */
object RidgelineTheme {
    /** Extra colour roles for the current light or dark mode, including [RidgelineColors.map]. */
    val colors: RidgelineColors
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineColors.current

    /** Stat, display, nav and map text styles. */
    val text: RidgelineTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineTextStyles.current

    /** Motion durations, already adjusted for reduced motion. */
    val motion: RidgelineMotion
        @Composable @ReadOnlyComposable
        get() = LocalRidgelineMotion.current

    /** Spacing scale and layout constants. */
    val spacing: Spacing get() = Spacing

    /** Fixed component sizes. */
    val sizes: Sizes get() = Sizes
}
