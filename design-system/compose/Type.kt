// Generated from design-system/tokens.json. Fonts are SIL OFL 1.1 (Manrope, Barlow Semi Condensed, JetBrains Mono).
package app.stravafit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import app.stravafit.R

/*
 * Option A (default, F-Droid friendly): bundle the OFL .ttf files in res/font/
 *   res/font/manrope_regular.ttf, manrope_semibold.ttf, manrope_bold.ttf
 *   res/font/barlow_semi_condensed_semibold.ttf, barlow_semi_condensed_bold.ttf
 * Option B: Google Fonts downloadable provider (needs Play Services; not for F-Droid builds).
 *   Requires res/values/font_certs.xml (com_google_android_gms_fonts_certs) and
 *   implementation("androidx.compose.ui:ui-text-google-fonts").
 */
const val USE_DOWNLOADABLE_FONTS = false

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun gf(name: String, vararg weights: FontWeight) =
    FontFamily(weights.map { androidx.compose.ui.text.googlefonts.Font(GoogleFont(name), provider, it) })

val UiFont: FontFamily = if (USE_DOWNLOADABLE_FONTS) gf("Manrope", FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold)
else FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val NumericFont: FontFamily = if (USE_DOWNLOADABLE_FONTS) gf("Barlow Semi Condensed", FontWeight.SemiBold, FontWeight.Bold)
else FontFamily(
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

private const val TNUM = "tnum"

private fun ui(size: Int, line: Int, w: FontWeight, track: Float = 0f) =
    TextStyle(fontFamily = UiFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = track.sp)

private fun num(size: Int, line: Int, w: FontWeight) =
    TextStyle(fontFamily = NumericFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, fontFeatureSettings = TNUM)

/** Material 3 slots mapped to StravaFit tokens. */
val FitTypography = Typography(
    displayLarge = num(64, 68, FontWeight.Bold),     // displayXl
    displayMedium = num(44, 48, FontWeight.Bold),    // displayL
    displaySmall = num(32, 36, FontWeight.SemiBold), // displayM
    headlineLarge = ui(24, 30, FontWeight.Bold),     // headline
    headlineMedium = ui(24, 30, FontWeight.Bold),
    headlineSmall = ui(20, 26, FontWeight.Bold),
    titleLarge = ui(20, 26, FontWeight.Bold),        // titleL
    titleMedium = ui(17, 22, FontWeight.Bold),       // titleM
    titleSmall = ui(15, 20, FontWeight.SemiBold),    // titleS
    bodyLarge = ui(16, 24, FontWeight.Normal),
    bodyMedium = ui(14, 20, FontWeight.Normal),
    bodySmall = ui(13, 18, FontWeight.Normal),
    labelLarge = ui(15, 20, FontWeight.SemiBold),
    labelMedium = ui(13, 16, FontWeight.SemiBold),
    labelSmall = ui(11, 14, FontWeight.SemiBold, 0.4f), // uppercase at call site
)

/** Styles with no M3 slot. */
@Immutable
data class FitTextStyles(
    val statM: TextStyle = num(22, 26, FontWeight.SemiBold),
    val statS: TextStyle = num(17, 20, FontWeight.SemiBold),
    val caption: TextStyle = ui(12, 16, FontWeight.Normal),
    val overline: TextStyle = ui(11, 14, FontWeight.SemiBold, 0.4f),
    /** Units render at ~60% of the value size in textMuted. */
    val unitScale: Float = 0.6f,
)

val LocalFitTextStyles = staticCompositionLocalOf { FitTextStyles() }
