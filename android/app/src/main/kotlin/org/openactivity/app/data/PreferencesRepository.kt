// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.model.toPreference
import org.openactivity.app.model.toSportOrNull
import org.openactivity.app.model.toSportType
import org.openactivity.app.model.toThemeMode
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.model.UnitSystem
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes [UserPreferences]: the app-wide values every screen renders with. */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setUnitSystem(unitSystem: UnitSystem)
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setLastSport(sport: Sport)
}

/**
 * [PreferencesRepository] over the single source of truth: units, theme and default sport live in
 * the `:core:data` [SettingsRepository] (which the recording service reads too); reduced motion is
 * an app-shell value in [LocalPreferences].
 */
@Singleton
class SettingsBackedPreferencesRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val local: LocalPreferences,
) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> =
        combine(settingsRepository.settings, local.reduceMotion) { settings, reduceMotion ->
            UserPreferences(
                unitSystem = settings.units,
                themeMode = settings.theme.toThemeMode(),
                reduceMotion = reduceMotion,
                lastSport = settings.defaultSport.toSportOrNull() ?: Sport.Run,
            )
        }

    override suspend fun setUnitSystem(unitSystem: UnitSystem) = settingsRepository.setUnits(unitSystem)

    override suspend fun setThemeMode(themeMode: ThemeMode) = settingsRepository.setTheme(themeMode.toPreference())

    override suspend fun setReduceMotion(enabled: Boolean) = local.setReduceMotion(enabled)

    override suspend fun setLastSport(sport: Sport) = settingsRepository.setDefaultSport(sport.toSportType())
}
