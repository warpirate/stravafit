// Generated from design-system/tokens.json. StravaFit "Ridgeline" Material 3 theme.
package app.stravafit.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Ref.Primary600, onPrimary = Ref.Slate0,
    primaryContainer = Ref.Primary100, onPrimaryContainer = Ref.Primary800,
    inversePrimary = Ref.Primary200,
    secondary = Ref.Slate600, onSecondary = Ref.Slate0,
    secondaryContainer = Ref.Slate100, onSecondaryContainer = Ref.Slate800,
    tertiary = Ref.Signal700, onTertiary = Ref.Slate900, // dark text on lime; white fails AA
    tertiaryContainer = Ref.Signal500, onTertiaryContainer = Ref.Slate850,
    background = Ref.Slate50, onBackground = Ref.Slate900,
    surface = Ref.Slate0, onSurface = Ref.Slate900,
    surfaceVariant = Ref.Slate25, onSurfaceVariant = Ref.Slate600,
    surfaceTint = Ref.Primary600,
    inverseSurface = Ref.Slate800, inverseOnSurface = Ref.Slate50,
    error = Ref.ErrorLight, onError = Ref.Slate0,
    errorContainer = Ref.ErrorContainerLight, onErrorContainer = Ref.OnErrorContainerLight,
    outline = Ref.Slate400, outlineVariant = Ref.Slate100,
    scrim = Ref.Slate950,
    surfaceDim = Ref.Slate100, surfaceBright = Ref.Slate0,
    surfaceContainerLowest = Ref.Slate0, surfaceContainerLow = Ref.Slate25,
    surfaceContainer = Ref.Slate50, surfaceContainerHigh = Ref.Slate100, surfaceContainerHighest = Ref.Slate200,
)

private val DarkScheme = darkColorScheme(
    primary = Ref.Primary300, onPrimary = Ref.Primary900,
    primaryContainer = Ref.Primary800, onPrimaryContainer = Ref.Primary100,
    inversePrimary = Ref.Primary600,
    secondary = Ref.Slate300, onSecondary = Ref.Slate900,
    secondaryContainer = Ref.Slate700, onSecondaryContainer = Ref.Slate100,
    tertiary = Ref.Signal300, onTertiary = Ref.Slate850,
    tertiaryContainer = Ref.Signal900, onTertiaryContainer = Ref.Signal100,
    background = Ref.Slate950, onBackground = Ref.Slate50,
    surface = Ref.Slate900, onSurface = Ref.Slate50,
    surfaceVariant = Ref.Slate850, onSurfaceVariant = Ref.Slate300,
    surfaceTint = Ref.Primary300,
    inverseSurface = Ref.Slate100, inverseOnSurface = Ref.Slate900,
    error = Ref.ErrorDark, onError = Ref.Slate950,
    errorContainer = Ref.ErrorContainerDark, onErrorContainer = Ref.OnErrorContainerDark,
    outline = Ref.Slate500, outlineVariant = Ref.Slate800,
    scrim = Ref.Slate950,
    surfaceDim = Ref.Slate950, surfaceBright = Ref.Slate700,
    surfaceContainerLowest = Ref.Slate950, surfaceContainerLow = Ref.Slate900,
    surfaceContainer = Ref.Slate900, surfaceContainerHigh = Ref.Slate850, surfaceContainerHighest = Ref.Slate800,
)

/**
 * Root theme. Dynamic color is intentionally OFF so the brand and zone colors stay stable
 * and contrast-verified. `reduceMotion` = in-app setting OR system animator scale of 0.
 */
@Composable
fun StravaFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemReduced = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val fit = if (darkTheme) DarkFitColors else LightFitColors
    CompositionLocalProvider(
        LocalFitColors provides fit,
        LocalFitTextStyles provides FitTextStyles(),
        LocalFitMotion provides FitMotion(reduced = reduceMotion || systemReduced),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = FitTypography,
            shapes = FitShapes,
            content = content,
        )
    }
}

/** Accessor: `FitTheme.colors.signal`, `FitTheme.text.statM`, `FitTheme.motion.medium`. */
object FitTheme {
    val colors: FitColors @Composable @ReadOnlyComposable get() = LocalFitColors.current
    val text: FitTextStyles @Composable @ReadOnlyComposable get() = LocalFitTextStyles.current
    val motion: FitMotion @Composable @ReadOnlyComposable get() = LocalFitMotion.current
    val spacing get() = Spacing
}
