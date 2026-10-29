// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Raw palette for design direction "strava" (ADR-014), taken from
 * `docs/design-directions/strava/tokens.json` (`color.light.*`, `color.dark.*`, `color.map.*`).
 *
 * UI code reads semantic roles from [androidx.compose.material3.MaterialTheme.colorScheme] or
 * [RidgelineTheme.colors]. Use these raw values only when defining a new role.
 *
 * The block at the end keeps the old Ridgeline names that code outside the theme package still
 * reads (`Ref.Slate900`, `Ref.InfoLight`, `Ref.Primary500` …), so this file drops in without
 * touching call sites. Prefer the new names in new code.
 */
object Ref {
    // Accent
    val Accent = Color(0xFFFC5200)
    val AccentPressLight = Color(0xFFE04A00)
    val AccentTextLight = Color(0xFFCC3D00) // small orange text on white, 5.0:1
    val AccentTextDark = Color(0xFFFF6A2B) // small orange text and pressed accent on dark, 6.2:1
    val AccentAreaLight = Color(0x24FC5200) // rgba(252,82,0,.14)
    val AccentAreaDark = Color(0x1AFC5200) // rgba(252,82,0,.10)
    val Paused = Color(0xFFC43B00) // paused strip, white 14/700 on it is 5.3:1

    // Neutrals, light
    val White = Color(0xFFFFFFFF)
    val GapLight = Color(0xFFEEEEF0)
    val Surface2Light = Color(0xFFF5F5F7)
    val Ink = Color(0xFF242428) // light text, on-medal, inverse pill
    val Text2Light = Color(0xFF45454E)
    val MutedLight = Color(0xFF6D6D78)
    val HairLight = Color(0xFFE5E5EA)
    val HairStrongLight = Color(0xFFCBCBD3)

    // Neutrals, dark
    val Black = Color(0xFF000000)
    val BgDark = Color(0xFF121214)
    val SurfaceDark = Color(0xFF1C1C1F)
    val Surface2Dark = Color(0xFF242428)
    val RaisedDark = Color(0xFF2A2A2F)
    val SheetOverMapDark = Color(0xFF232327)
    val TextDark = Color(0xFFF2F2F4)
    val Text2Dark = Color(0xFFD2D2D8)
    val MutedDark = Color(0xFFA3A3AD)
    val HairDark = Color(0xFF2E2E34)
    val HairStrongDark = Color(0xFF43434B)
    val OnPillInverseDark = Color(0xFF18181B)

    // Status and data
    val OkLight = Color(0xFF16884A)
    val OkDark = Color(0xFF3CC47A)
    val Recording = Color(0xFFE5322D)
    val PausedDot = Color(0xFFE8A317)
    val ErrorLight = Color(0xFFC4281F) // derived: recording red darkened to 5.7:1 on white
    val ErrorDark = Color(0xFFFF5C5C) // derived
    val HrLight = Color(0xFFE0245E)
    val HrDark = Color(0xFFFF5C8A)
    val LocLight = Color(0xFF2F74F0)
    val LocDark = Color(0xFF4C8DFF)
    val BarLight = Color(0xFFC3CCD8)
    val BarDark = Color(0xFF424B59)
    val ChartLineLight = Color(0xFF3C78D8)
    val ChartLineDark = Color(0xFF6FA2F5)
    val ChartElevLight = Color(0xFFE3E3E8)
    val ChartElevDark = Color(0xFF2C2C33)
    val RouteStart = Color(0xFF1FA45B)

    // Medals: numeral is always [Ink] on every metal
    val GoldLight = Color(0xFFD6A01E)
    val GoldDark = Color(0xFFE7B43A)
    val SilverLight = Color(0xFF9DA5AF)
    val SilverDark = Color(0xFFB4BBC4)
    val Bronze = Color(0xFFC98A55)

    // ---- Legacy Ridgeline names still read outside the theme package ----
    /** Dark text on light avatar tints. */
    val Slate900 = Ink
    /** Planned-route blue on maps. */
    val InfoLight = LocLight
    val InfoDark = LocDark
    /** Location puck. */
    val Primary500 = LocLight
    val Primary300 = LocDark
    val MedalGold = GoldLight
    val MedalSilver = SilverLight
    val MedalBronze = Bronze
}

/** Basemap style (`color.map.*`). Feed MapLibre style JSON from these. */
@Immutable
data class MapColors(
    val land: Color,
    val built: Color,
    val block: Color,
    val park: Color,
    val path: Color,
    val water: Color,
    val road: Color,
    val roadCase: Color,
    val major: Color,
    val majorCase: Color,
    val arterial: Color,
    val arterialCase: Color,
    val rail: Color,
    val poi: Color,
    val label: Color,
    val roadLabel: Color,
    val waterLabel: Color,
    val parkLabel: Color,
    val halo: Color,
    val route: Color,
    val routeCase: Color,
    val heat: Color,
    val heatCore: Color,
    /** Opacity of faint heat on minor-street runs. */
    val heatFaintAlpha: Float,
)

val LightMapColors = MapColors(
    land = Color(0xFFF2F1EC), built = Color(0xFFECEAE3), block = Color(0xFFE9E7E0),
    park = Color(0xFFCFE5BF), path = Color(0xFFAFCB9A), water = Color(0xFFA7D0EA),
    road = Color(0xFFFFFFFF), roadCase = Color(0xFFE2DED5),
    major = Color(0xFFFFFFFF), majorCase = Color(0xFFC7C0B2),
    arterial = Color(0xFFFCE7B0), arterialCase = Color(0xFFDDBA6E),
    rail = Color(0xFFB4B0A6), poi = Color(0xFF8A8790),
    label = Color(0xFF5A5B61), roadLabel = Color(0xFF55565C),
    waterLabel = Color(0xFF33689A), parkLabel = Color(0xFF3B6A2E), halo = Color(0xFFF4F3EF),
    route = Ref.Accent, routeCase = Color(0xFFFFFFFF),
    heat = Color(0xFFEE3F0A), heatCore = Color(0xFFC42600), heatFaintAlpha = 0.24f,
)

val DarkMapColors = MapColors(
    land = Color(0xFF1A1C20), built = Color(0xFF1D2025), block = Color(0xFF1F2227),
    park = Color(0xFF18291E), path = Color(0xFF2C4434), water = Color(0xFF11314A),
    road = Color(0xFF2B2F36), roadCase = Color(0xFF1A1C20),
    major = Color(0xFF3A3F48), majorCase = Color(0xFF262A30),
    arterial = Color(0xFF4B4535), arterialCase = Color(0xFF2F2B22),
    rail = Color(0xFF454A53), poi = Color(0xFF7D828C),
    label = Color(0xFFA2A6B0), roadLabel = Color(0xFFB3B7C0),
    waterLabel = Color(0xFF78A6CF), parkLabel = Color(0xFF86B48A), halo = Color(0xFF1A1C20),
    route = Ref.Accent, routeCase = Color(0xFF121214),
    heat = Color(0xFFFF6A12), heatCore = Color(0xFFFFC46B), heatFaintAlpha = 0.30f,
)

/**
 * App colour roles that the Material 3 `ColorScheme` does not cover.
 *
 * The first group keeps the Ridgeline field names so existing call sites compile; each KDoc names
 * the strava token it now maps to. The second group holds the strava-only roles.
 */
@Immutable
data class RidgelineColors(
    // ---- Ridgeline-compatible roles ----
    /** `gap`: band between edge-to-edge cards. */
    val canvas: Color,
    /** `surface-2`. */
    val surfaceSunken: Color,
    /** `text-2`. */
    val textSecondary: Color,
    /** `muted`. Never on `gap` in light (4.4:1). */
    val textMuted: Color,
    /** `hair`. */
    val hairline: Color,
    /** `hair-strong`. */
    val borderStrong: Color,
    /** Achievement fill = `gold`. Pair with [onSignal]. */
    val signal: Color,
    /** `on-medal`. */
    val onSignal: Color,
    /** Achievement text on surface = `text` (Strava bolds the count rather than colouring it). */
    val signalText: Color,
    /** `ok`. */
    val success: Color,
    /** Derived: `ok` at low alpha over surface. */
    val successContainer: Color,
    /** Derived: amber for warnings (paused dot family). */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** `loc`. */
    val info: Color,
    /** `accent`: the user's own route. */
    val routeSelf: Color,
    /** `route-case`. */
    val routeCasing: Color,
    /** Other people's routes: `muted`. */
    val routeOther: Color,
    /** `accent`. */
    val focusRing: Color,
    val mapLand: Color,
    val mapWater: Color,
    val mapRoad: Color,
    /** Skeleton blocks: `hair` (light) / `surface-2` (dark). */
    val skeleton: Color,
    val medalGold: Color,
    val medalSilver: Color,
    val medalBronze: Color,
    val viz: VizColors,

    // ---- strava roles ----
    /** `accent` #FC5200: fills, icons, route, record button, tab underline. Text only at >=19sp/800. */
    val accent: Color,
    val accentPress: Color,
    /** `accent-text`: every small orange label (See all, active nav label, links). */
    val accentText: Color,
    val onAccent: Color,
    val accentArea: Color,
    /** `gap`, same as [canvas]. */
    val gap: Color,
    /** `raised`: dialogs. */
    val raised: Color,
    /** `float`: chips, round buttons and search pill over maps (with shadow). */
    val float: Color,
    val onFloat: Color,
    /** Bottom sheet and record panel over a map (`#232327` in dark). */
    val sheetOverMap: Color,
    /** Inverted chip, active layer chip, Create route pill. */
    val pillInverse: Color,
    val onPillInverse: Color,
    /** Paused strip fill, with [onPaused] 14/700. */
    val paused: Color,
    val onPaused: Color,
    /** Red dot in the "Recording" pill. */
    val recording: Color,
    /** Amber dot in the "Paused" pill. */
    val pausedDot: Color,
    val heartRate: Color,
    /** Live location dot and GPS halo. */
    val location: Color,
    val splitBar: Color,
    val splitBarFast: Color,
    val chartLine: Color,
    val chartElevation: Color,
    val routeStart: Color,
    /** Medal numeral on every metal. */
    val onMedal: Color,
    /** Status-bar icons and clock. */
    val statusBarContent: Color,
    val map: MapColors,
)

/** Data-visualisation colours. Zones Z1..Z5 are ordered by lightness; always print the zone label too. */
@Immutable
data class VizColors(
    /** Index 0 = Z1 Recovery … 4 = Z5 Max. Not specified by the strava direction; kept from Ridgeline. */
    val zones: List<Color>,
    val pace: Color,
    val heartRate: Color,
    val cadence: Color,
    val power: Color,
    val elevationFill: Color,
    val elevationLine: Color,
    val compare: Color,
    val goalLine: Color,
    val categorical: List<Color>,
)

/** Pattern backups for zones, so zone colour never carries meaning alone. */
enum class ZonePattern { None, Dots, Diagonal, CrossHatch, Solid }

/** Zone patterns in Z1..Z5 order. */
val ZonePatterns: List<ZonePattern> =
    listOf(ZonePattern.None, ZonePattern.Dots, ZonePattern.Diagonal, ZonePattern.CrossHatch, ZonePattern.Solid)

/** Light roles. */
val LightRidgelineColors = RidgelineColors(
    canvas = Ref.GapLight,
    surfaceSunken = Ref.Surface2Light,
    textSecondary = Ref.Text2Light,
    textMuted = Ref.MutedLight,
    hairline = Ref.HairLight,
    borderStrong = Ref.HairStrongLight,
    signal = Ref.GoldLight,
    onSignal = Ref.Ink,
    signalText = Ref.Ink,
    success = Ref.OkLight,
    successContainer = Color(0xFFE3F2E9),
    warning = Color(0xFF9A6200),
    warningContainer = Color(0xFFFBEFD6),
    onWarningContainer = Color(0xFF5C3B00),
    info = Ref.LocLight,
    routeSelf = Ref.Accent,
    routeCasing = LightMapColors.routeCase,
    routeOther = Ref.MutedLight,
    focusRing = Ref.Accent,
    mapLand = LightMapColors.land,
    mapWater = LightMapColors.water,
    mapRoad = LightMapColors.road,
    skeleton = Ref.HairLight,
    medalGold = Ref.GoldLight,
    medalSilver = Ref.SilverLight,
    medalBronze = Ref.Bronze,
    viz = VizColors(
        zones = listOf(Color(0xFFBFD9EA), Color(0xFF6FA8D0), Color(0xFFE9C46A), Color(0xFFE07B39), Color(0xFF9E2A4B)),
        pace = Ref.ChartLineLight,
        heartRate = Ref.HrLight,
        cadence = Color(0xFF6B5BB5),
        power = Ref.OkLight,
        elevationFill = Ref.ChartElevLight,
        elevationLine = Ref.HairStrongLight,
        compare = Ref.MutedLight,
        goalLine = Ref.Text2Light,
        categorical = listOf(Ref.Accent, Ref.ChartLineLight, Ref.HrLight, Ref.OkLight, Ref.GoldLight),
    ),
    accent = Ref.Accent,
    accentPress = Ref.AccentPressLight,
    accentText = Ref.AccentTextLight,
    onAccent = Ref.White,
    accentArea = Ref.AccentAreaLight,
    gap = Ref.GapLight,
    raised = Ref.White,
    float = Ref.White,
    onFloat = Ref.Ink,
    sheetOverMap = Ref.White,
    pillInverse = Ref.Ink,
    onPillInverse = Ref.White,
    paused = Ref.Paused,
    onPaused = Ref.White,
    recording = Ref.Recording,
    pausedDot = Ref.PausedDot,
    heartRate = Ref.HrLight,
    location = Ref.LocLight,
    splitBar = Ref.BarLight,
    splitBarFast = Ref.Accent,
    chartLine = Ref.ChartLineLight,
    chartElevation = Ref.ChartElevLight,
    routeStart = Ref.RouteStart,
    onMedal = Ref.Ink,
    statusBarContent = Ref.Ink,
    map = LightMapColors,
)

/** Dark roles. */
val DarkRidgelineColors = RidgelineColors(
    canvas = Ref.Black,
    surfaceSunken = Ref.Surface2Dark,
    textSecondary = Ref.Text2Dark,
    textMuted = Ref.MutedDark,
    hairline = Ref.HairDark,
    borderStrong = Ref.HairStrongDark,
    signal = Ref.GoldDark,
    onSignal = Ref.Ink,
    signalText = Ref.TextDark,
    success = Ref.OkDark,
    successContainer = Color(0xFF13301F),
    warning = Ref.PausedDot,
    warningContainer = Color(0xFF3A2A06),
    onWarningContainer = Color(0xFFFBEFD6),
    info = Ref.LocDark,
    routeSelf = Ref.Accent,
    routeCasing = DarkMapColors.routeCase,
    routeOther = Ref.MutedDark,
    focusRing = Ref.Accent,
    mapLand = DarkMapColors.land,
    mapWater = DarkMapColors.water,
    mapRoad = DarkMapColors.road,
    skeleton = Ref.Surface2Dark,
    medalGold = Ref.GoldDark,
    medalSilver = Ref.SilverDark,
    medalBronze = Ref.Bronze,
    viz = VizColors(
        zones = listOf(Color(0xFF9CC3DD), Color(0xFF6FA8D0), Color(0xFFEBCB7C), Color(0xFFEA8F52), Color(0xFFD0557A)),
        pace = Ref.ChartLineDark,
        heartRate = Ref.HrDark,
        cadence = Color(0xFFA497E0),
        power = Ref.OkDark,
        elevationFill = Ref.ChartElevDark,
        elevationLine = Ref.HairStrongDark,
        compare = Ref.MutedDark,
        goalLine = Ref.Text2Dark,
        categorical = listOf(Ref.Accent, Ref.ChartLineDark, Ref.HrDark, Ref.OkDark, Ref.GoldDark),
    ),
    accent = Ref.Accent,
    accentPress = Ref.AccentTextDark,
    accentText = Ref.AccentTextDark,
    onAccent = Ref.White,
    accentArea = Ref.AccentAreaDark,
    gap = Ref.Black,
    raised = Ref.RaisedDark,
    float = Ref.RaisedDark,
    onFloat = Ref.TextDark,
    sheetOverMap = Ref.SheetOverMapDark,
    pillInverse = Ref.TextDark,
    onPillInverse = Ref.OnPillInverseDark,
    paused = Ref.Paused,
    onPaused = Ref.White,
    recording = Ref.Recording,
    pausedDot = Ref.PausedDot,
    heartRate = Ref.HrDark,
    location = Ref.LocDark,
    splitBar = Ref.BarDark,
    splitBarFast = Ref.Accent,
    chartLine = Ref.ChartLineDark,
    chartElevation = Ref.ChartElevDark,
    routeStart = Ref.RouteStart,
    onMedal = Ref.Ink,
    statusBarContent = Ref.TextDark,
    map = DarkMapColors,
)

/** Composition local backing [RidgelineTheme.colors]. */
val LocalRidgelineColors = staticCompositionLocalOf { LightRidgelineColors }
