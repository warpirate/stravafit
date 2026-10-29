// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.model.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes [UserPreferences]. */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setUnitSystem(unitSystem: UnitSystem)
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setLastSport(sport: Sport)
}

/** [PreferencesRepository] backed by Jetpack DataStore. Unknown stored values fall back to defaults. */
@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val defaults = UserPreferences()
        UserPreferences(
            unitSystem = prefs[Keys.UNITS].toEnum(defaults.unitSystem),
            themeMode = prefs[Keys.THEME].toEnum(defaults.themeMode),
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            lastSport = prefs[Keys.LAST_SPORT].toEnum(defaults.lastSport),
        )
    }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) {
        dataStore.edit { it[Keys.UNITS] = unitSystem.name }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = themeMode.name }
    }

    override suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    override suspend fun setLastSport(sport: Sport) {
        dataStore.edit { it[Keys.LAST_SPORT] = sport.name }
    }

    private object Keys {
        val UNITS = stringPreferencesKey("unit_system")
        val THEME = stringPreferencesKey("theme_mode")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val LAST_SPORT = stringPreferencesKey("last_sport")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
