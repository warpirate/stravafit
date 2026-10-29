// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

import org.openactivity.core.datastore.ThemePreference

/** The stored [ThemePreference] for this [ThemeMode]. */
fun ThemeMode.toPreference(): ThemePreference = when (this) {
    ThemeMode.System -> ThemePreference.SYSTEM
    ThemeMode.Light -> ThemePreference.LIGHT
    ThemeMode.Dark -> ThemePreference.DARK
}

/** The [ThemeMode] for a stored [ThemePreference]. */
fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.SYSTEM -> ThemeMode.System
    ThemePreference.LIGHT -> ThemeMode.Light
    ThemePreference.DARK -> ThemeMode.Dark
}
