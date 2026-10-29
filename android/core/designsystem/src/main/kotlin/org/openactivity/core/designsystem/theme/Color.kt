// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ridgeline reference palette (`color.ref.*` in `design-system/tokens.json`).
 *
 * UI code should read semantic roles from [androidx.compose.material3.MaterialTheme.colorScheme]
 * or [RidgelineTheme.colors]; reach for these raw values only when defining new roles.
 */
object Ref {
    // Primary "Fjord" teal
    val Primary50 = Color(0xFFE6F4F5)
    val Primary100 = Color(0xFFC2E3E6)
    val Primary200 = Color(0xFF8ECBD1)
    val Primary300 = Color(0xFF57B0B9)
    val Primary400 = Color(0xFF2B97A2)
    val Primary500 = Color(0xFF0F7E8A)
    val Primary600 = Color(0xFF0B6A75)
    val Primary700 = Color(0xFF08565F)
    val Primary800 = Color(0xFF064249)
    val Primary900 = Color(0xFF032C31)

    // Signal "Lichen" lime: achievements and celebrations, used as a fill only
    val Signal100 = Color(0xFFF3FBD6)
    val Signal300 = Color(0xFFDDF27F)
    val Signal500 = Color(0xFFB8DB2E)
    val Signal700 = Color(0xFF7E9A12)
    val Signal900 = Color(0xFF3F4E07)

    // Neutrals "Slate"
    val Slate0 = Color(0xFFFFFFFF)
    val Slate25 = Color(0xFFF8FAFA)
    val Slate50 = Color(0xFFF1F4F5)
    val Slate100 = Color(0xFFE3E8EA)
    val Slate200 = Color(0xFFCBD3D6)
    val Slate300 = Color(0xFFA9B4B8)
    val Slate400 = Color(0xFF87939A)
    val Slate500 = Color(0xFF5E6B72)
    val Slate600 = Color(0xFF4D585E)
    val Slate700 = Color(0xFF363F44)
    val Slate800 = Color(0xFF22292D)
    val Slate850 = Color(0xFF1A2024)
    val Slate900 = Color(0xFF12171A)
    val Slate950 = Color(0xFF0B0F11)

    // Status
    val SuccessLight = Color(0xFF187A4D)
    val SuccessDark = Color(0xFF5CC48F)
    val SuccessContainerLight = Color(0xFFDDF3E7)
    val SuccessContainerDark = Color(0xFF0F3A26)
    val WarningLight = Color(0xFF9A6416)
    val WarningDark = Color(0xFFE5B45E)
    val WarningContainerLight = Color(0xFFFBEFD8)
    val WarningContainerDark = Color(0xFF3E2A08)
    val OnWarningContainerLight = Color(0xFF6B4409)
    val OnWarningContainerDark = Color(0xFFFBEFD8)
    val ErrorLight = Color(0xFFC2362F)
    val ErrorDark = Color(0xFFF08A84)
    val ErrorContainerLight = Color(0xFFFBE1DF)
    val ErrorContainerDark = Color(0xFF4A1411)
    val OnErrorContainerLight = Color(0xFF8A1E19)
    val OnErrorContainerDark = Color(0xFFFBD3D0)
    val InfoLight = Color(0xFF2F6FB3)
    val InfoDark = Color(0xFF86B4E6)

    // Medals: always paired with the numeral 1/2/3 in slate-900
    val MedalGold = Color(0xFFC99A2E)
    val MedalSilver = Color(0xFF8F9AA3)
    val MedalBronze = Color(0xFFBA7842)
}

/** App-specific colour roles that the Material 3 `ColorScheme` does not cover. */
@Immutable
data class RidgelineColors(
    val canvas: Color,
    val surfaceSunken: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val hairline: Color,
    val borderStrong: Color,
    /** Achievement fill. Never use as text on light surfaces; pair with [onSignal]. */
    val signal: Color,
    val onSignal: Color,
    /** Achievement colour that is safe as text on `surface`. */
    val signalText: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val routeSelf: Color,
    val routeCasing: Color,
    val routeOther: Color,
    val focusRing: Color,
    /** Land fill for map placeholders drawn before MapLibre tiles are wired in. */
    val mapLand: Color,
    /** Water fill for map placeholders. */
    val mapWater: Color,
    /** Street lines for map placeholders. */
    val mapRoad: Color,
    /** Fill of loading skeleton blocks. */
    val skeleton: Color,
    val medalGold: Color = Ref.MedalGold,
    val medalSilver: Color = Ref.MedalSilver,
    val medalBronze: Color = Ref.MedalBronze,
    val viz: VizColors,
)

/** Data-visualisation colours. Zones Z1..Z5 are ordered by lightness; always print the zone label too. */
@Immutable
data class VizColors(
    /** Index 0 = Z1 Recovery … 4 = Z5 Max. */
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

/** Light-mode Ridgeline roles. */
val LightRidgelineColors = RidgelineColors(
    canvas = Ref.Slate50,
    surfaceSunken = Ref.Slate25,
    textSecondary = Ref.Slate600,
    textMuted = Ref.Slate500,
    hairline = Ref.Slate100,
    borderStrong = Ref.Slate400,
    signal = Ref.Signal500,
    onSignal = Ref.Slate850,
    signalText = Ref.Signal900,
    success = Ref.SuccessLight,
    successContainer = Ref.SuccessContainerLight,
    warning = Ref.WarningLight,
    warningContainer = Ref.WarningContainerLight,
    onWarningContainer = Ref.OnWarningContainerLight,
    info = Ref.InfoLight,
    routeSelf = Ref.Primary500,
    routeCasing = Ref.Slate0,
    routeOther = Ref.Slate500,
    focusRing = Ref.Primary400,
    mapLand = Color(0xFFE8EEEB),
    mapWater = Color(0xFFCFE3EA),
    mapRoad = Ref.Slate0,
    skeleton = Ref.Slate100,
    viz = VizColors(
        zones = listOf(Color(0xFFBFD9EA), Color(0xFF6FA8D0), Color(0xFFE9C46A), Color(0xFFE07B39), Color(0xFF9E2A4B)),
        pace = Color(0xFF0B6A75),
        heartRate = Color(0xFFB23A5A),
        cadence = Color(0xFF6B5BB5),
        power = Color(0xFF3C8D5A),
        elevationFill = Color(0xFFCBD3D6),
        elevationLine = Color(0xFF87939A),
        compare = Color(0xFF87939A),
        goalLine = Color(0xFF4D585E),
        categorical = listOf(Color(0xFF0B6A75), Color(0xFFB23A5A), Color(0xFF6B5BB5), Color(0xFF3C8D5A), Color(0xFF9A6416)),
    ),
)

/** Dark-mode Ridgeline roles. */
val DarkRidgelineColors = RidgelineColors(
    canvas = Ref.Slate950,
    surfaceSunken = Ref.Slate950,
    textSecondary = Ref.Slate300,
    textMuted = Ref.Slate400,
    hairline = Ref.Slate800,
    borderStrong = Ref.Slate500,
    signal = Ref.Signal300,
    onSignal = Ref.Slate850,
    signalText = Ref.Signal300,
    success = Ref.SuccessDark,
    successContainer = Ref.SuccessContainerDark,
    warning = Ref.WarningDark,
    warningContainer = Ref.WarningContainerDark,
    onWarningContainer = Ref.OnWarningContainerDark,
    info = Ref.InfoDark,
    routeSelf = Ref.Primary300,
    routeCasing = Ref.Slate950,
    routeOther = Ref.Slate300,
    focusRing = Ref.Primary200,
    mapLand = Color(0xFF1B2326),
    mapWater = Color(0xFF13252B),
    mapRoad = Ref.Slate700,
    skeleton = Ref.Slate800,
    viz = VizColors(
        zones = listOf(Color(0xFF9CC3DD), Color(0xFF6FA8D0), Color(0xFFEBCB7C), Color(0xFFEA8F52), Color(0xFFD0557A)),
        pace = Color(0xFF57B0B9),
        heartRate = Color(0xFFE07A97),
        cadence = Color(0xFFA497E0),
        power = Color(0xFF7BC89A),
        elevationFill = Color(0xFF363F44),
        elevationLine = Color(0xFF87939A),
        compare = Color(0xFF87939A),
        goalLine = Color(0xFFA9B4B8),
        categorical = listOf(Color(0xFF57B0B9), Color(0xFFE07A97), Color(0xFFA497E0), Color(0xFF7BC89A), Color(0xFFE5B45E)),
    ),
)

/** Composition local backing [RidgelineTheme.colors]. */
val LocalRidgelineColors = staticCompositionLocalOf { LightRidgelineColors }
