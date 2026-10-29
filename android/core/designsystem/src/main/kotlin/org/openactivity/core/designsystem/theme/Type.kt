// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.R

/*
 * Font: Figtree (SIL OFL 1.1), weights 400/500/600/700/800 (`font.family.ui` in tokens.json).
 *
 * Bundled (default, works offline and without Play Services). Copy into core/designsystem:
 *   src/main/res/font/figtree_regular.ttf    (400)
 *   src/main/res/font/figtree_medium.ttf     (500)
 *   src/main/res/font/figtree_semibold.ttf   (600)
 *   src/main/res/font/figtree_bold.ttf       (700)
 *   src/main/res/font/figtree_extrabold.ttf  (800)
 *   src/main/assets/licenses/OFL-Figtree.txt
 * Source: https://github.com/erikdkennedy/figtree (fonts/ttf). The Manrope and Barlow files can be
 * deleted once nothing else references them.
 *
 * Alternative, downloadable (needs androidx.compose.ui:ui-text-google-fonts and Play Services):
 *   val provider = GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms",
 *       R.array.com_google_android_gms_fonts_certs)
 *   val UiFont = FontFamily(listOf(400, 500, 600, 700, 800).map {
 *       Font(GoogleFont("Figtree"), provider, FontWeight(it)) })
 */

/** Figtree: every UI string. */
val UiFont: FontFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

/** Numerals use the same family as text; numeric styles add tabular figures. */
val NumericFont: FontFamily = UiFont

private const val TABULAR_NUMS = "tnum"

/** −0.02em tracking used on stat-m and larger numerals (narrows Figtree's round 1, 2 and 4). */
private val TightStat: TextUnit = (-0.02).em

private fun ui(size: Float, line: Float, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = UiFont,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    fontWeight = weight,
    letterSpacing = tracking,
)

private fun num(size: Float, line: Float, weight: FontWeight, tracking: TextUnit = 0.sp) =
    ui(size, line, weight, tracking).copy(fontFamily = NumericFont, fontFeatureSettings = TABULAR_NUMS)

/**
 * Material 3 slots mapped to strava type tokens (size / line-height multiplier / weight).
 *
 * TopAppBar reads titleLarge, buttons read labelLarge, NavigationBar reads labelMedium: pass
 * [RidgelineTextStyles.nav] to nav items explicitly to get the 11/600 label.
 */
val RidgelineTypography = Typography(
    displayLarge = num(84f, 1.0f, FontWeight.ExtraBold, (-2.5).sp), // display: live Time
    displayMedium = num(52f, 1.05f, FontWeight.Bold, (-1).sp), // stat-xl: live Split Avg, Distance
    displaySmall = num(26f, 1.15f, FontWeight.Bold, TightStat), // stat-l: weekly totals
    headlineLarge = ui(22f, 1.2f, FontWeight.ExtraBold), // title-xl: activity title
    headlineMedium = ui(21f, 1.2f, FontWeight.Bold, (-0.2).sp), // top-bar title
    headlineSmall = ui(19f, 1.25f, FontWeight.Bold, (-0.2).sp), // title-l
    titleLarge = ui(21f, 1.2f, FontWeight.Bold, (-0.2).sp), // top-bar title ("Home", "You")
    titleMedium = ui(19f, 1.25f, FontWeight.Bold, (-0.2).sp), // title-l: feed title, section headers
    titleSmall = ui(15f, 1.25f, FontWeight.Bold), // title-m: athlete name, route card name
    bodyLarge = ui(15f, 1.45f, FontWeight.Normal), // body
    bodyMedium = ui(14f, 1.4f, FontWeight.Normal), // body-s
    bodySmall = ui(13f, 1.35f, FontWeight.Normal), // meta
    labelLarge = ui(14f, 1.2f, FontWeight.Bold), // button: selector pills
    labelMedium = ui(12f, 1.3f, FontWeight.Medium), // label: stat labels (Title Case)
    labelSmall = ui(11f, 1.2f, FontWeight.SemiBold, 0.1.sp), // nav
)

/** strava text styles with no Material 3 slot. */
@Immutable
data class RidgelineTextStyles(
    /** display 84/1.0/800, −2.5: live Time. */
    val display: TextStyle = num(84f, 1.0f, FontWeight.ExtraBold, (-2.5).sp),
    /** display when paused: 72, −2. */
    val displayPaused: TextStyle = num(72f, 1.0f, FontWeight.ExtraBold, (-2).sp),
    /** stat-xl 52/1.05/700, −1. Paused rows use 48. */
    val statXl: TextStyle = num(52f, 1.05f, FontWeight.Bold, (-1).sp),
    /** stat-l 26/1.15/700, −0.02em. */
    val statL: TextStyle = num(26f, 1.15f, FontWeight.Bold, TightStat),
    /** stat-m 20/1.2/700, −0.02em: feed and detail values. */
    val statM: TextStyle = num(20f, 1.2f, FontWeight.Bold, TightStat),
    /** 15/700 tabular: splits table, small stats on route cards. */
    val statS: TextStyle = num(15f, 1.25f, FontWeight.Bold),
    /** label 12/1.3/500: stat labels in Title Case, `muted`. */
    val caption: TextStyle = ui(12f, 1.3f, FontWeight.Medium),
    /** Record-screen label 13/600 that carries the unit: "Split Avg (/km)". */
    val labelRecord: TextStyle = ui(13f, 1.3f, FontWeight.SemiBold),
    /** Uppercase ticks (AUG SEP OCT); callers upper-case the string. */
    val overline: TextStyle = ui(11f, 1.2f, FontWeight.SemiBold, 0.4.sp),
    /** Bottom-nav label 11/600, +0.1. */
    val nav: TextStyle = ui(11f, 1.2f, FontWeight.SemiBold, 0.1.sp),
    /** Tabs and feed switcher 15/600. */
    val tab: TextStyle = ui(15f, 1.2f, FontWeight.SemiBold),
    /** Start / Finish on accent: 20/800 (Finish 19). The only orange-fill label size allowed. */
    val buttonHero: TextStyle = ui(20f, 1.0f, FontWeight.ExtraBold),
    /** Map area label 10/700, +1.4, uppercase, with a halo. */
    val mapArea: TextStyle = ui(10f, 1.0f, FontWeight.Bold, 1.4.sp),
    /** Map road label 9.5/600, with a halo. */
    val mapRoad: TextStyle = ui(9.5f, 1.0f, FontWeight.SemiBold),
    /** Units beside values render at this fraction of the value size, weight 600, no tracking. */
    val unitScale: Float = 0.65f,
)

/** Composition local backing [RidgelineTheme.text]. */
val LocalRidgelineTextStyles = staticCompositionLocalOf { RidgelineTextStyles() }
