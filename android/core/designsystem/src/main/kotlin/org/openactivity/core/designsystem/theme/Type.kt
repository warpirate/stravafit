// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.R

/*
 * Fonts are bundled from res/font (SIL OFL 1.1, licence texts in assets/licenses/) so the
 * F-Droid build needs no Play Services font provider.
 */

/** Manrope: every UI string. */
val UiFont: FontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/** Barlow Semi Condensed: numerals and stat values, always with tabular figures. */
val NumericFont: FontFamily = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

private const val TABULAR_NUMS = "tnum"

private fun ui(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = UiFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

private fun num(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = NumericFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    fontFeatureSettings = TABULAR_NUMS,
)

/** Material 3 type slots mapped to Ridgeline tokens. */
val RidgelineTypography = Typography(
    displayLarge = num(64, 68, FontWeight.Bold), // displayXl
    displayMedium = num(44, 48, FontWeight.Bold), // displayL
    displaySmall = num(32, 36, FontWeight.SemiBold), // displayM
    headlineLarge = ui(24, 30, FontWeight.Bold), // headline
    headlineMedium = ui(24, 30, FontWeight.Bold),
    headlineSmall = ui(20, 26, FontWeight.Bold),
    titleLarge = ui(20, 26, FontWeight.Bold), // titleL
    titleMedium = ui(17, 22, FontWeight.Bold), // titleM
    titleSmall = ui(15, 20, FontWeight.SemiBold), // titleS
    bodyLarge = ui(16, 24, FontWeight.Normal),
    bodyMedium = ui(14, 20, FontWeight.Normal),
    bodySmall = ui(13, 18, FontWeight.Normal),
    labelLarge = ui(15, 20, FontWeight.SemiBold),
    labelMedium = ui(13, 16, FontWeight.SemiBold),
    labelSmall = ui(11, 14, FontWeight.SemiBold, 0.4f), // uppercase at the call site
)

/** Ridgeline text styles that have no Material 3 slot. */
@Immutable
data class RidgelineTextStyles(
    val statL: TextStyle = num(28, 32, FontWeight.SemiBold),
    val statM: TextStyle = num(22, 26, FontWeight.SemiBold),
    val statS: TextStyle = num(17, 20, FontWeight.SemiBold),
    val caption: TextStyle = ui(12, 16, FontWeight.Normal),
    /** Uppercase overline label; callers upper-case the string. */
    val overline: TextStyle = ui(11, 14, FontWeight.SemiBold, 0.4f),
    /** Units render at this fraction of the value size, in `textMuted`. */
    val unitScale: Float = 0.6f,
)

/** Composition local backing [RidgelineTheme.text]. */
val LocalRidgelineTextStyles = staticCompositionLocalOf { RidgelineTextStyles() }
