// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

/** Measurement system for distances, paces and elevation. */
enum class UnitSystem(val label: String) {
    Metric("Metric (km, m)"),
    Imperial("Imperial (mi, ft)"),
}

/** Appearance preference. */
enum class ThemeMode(val label: String) {
    System("System default"),
    Light("Light"),
    Dark("Dark"),
}

/** User preferences that shape how every screen renders. */
data class UserPreferences(
    val unitSystem: UnitSystem = UnitSystem.Metric,
    val themeMode: ThemeMode = ThemeMode.System,
    val reduceMotion: Boolean = false,
    /** Sport preselected on the Record screen (record checklist 2.2). */
    val lastSport: Sport = Sport.Run,
)
