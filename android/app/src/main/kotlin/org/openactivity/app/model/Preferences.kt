// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

import org.openactivity.model.UnitSystem

/** Appearance preference. */
enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

/** User preferences that shape how every screen renders. */
data class UserPreferences(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val themeMode: ThemeMode = ThemeMode.System,
    val reduceMotion: Boolean = false,
    /** Sport preselected on the Record screen (record checklist 2.2). */
    val lastSport: Sport = Sport.Run,
)

/** Display label of a unit system, e.g. "Metric (km, m)". */
val UnitSystem.label: String
    get() = when (this) {
        UnitSystem.METRIC -> "Metric (km, m)"
        UnitSystem.IMPERIAL -> "Imperial (mi, ft)"
    }
