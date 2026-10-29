// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.io.IOException
import java.util.Locale

/**
 * Reads and writes [AppSettings] in a Preferences DataStore.
 *
 * Values are stored by stable enum names; unknown or invalid stored values fall back to defaults, so a
 * downgrade or a corrupt value never crashes the app. An unreadable file is treated as empty.
 *
 * @param locale decides the first-run unit system while no unit has been chosen.
 */
class SettingsDataSource(
    private val dataStore: DataStore<Preferences>,
    private val locale: () -> Locale = Locale::getDefault,
) {
    /** The current settings; emits again whenever anything changes. */
    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    suspend fun setUnits(units: UnitSystem) = edit { it[Keys.UNITS] = units.name }

    suspend fun setTheme(theme: ThemePreference) = edit { it[Keys.THEME] = theme.name }

    /** Turns auto-pause on or off for one sport. */
    suspend fun setAutoPause(sport: SportType, enabled: Boolean) = edit { prefs ->
        val on = prefs[Keys.AUTO_PAUSE_ON].orEmpty() - sport.name
        val off = prefs[Keys.AUTO_PAUSE_OFF].orEmpty() - sport.name
        prefs[Keys.AUTO_PAUSE_ON] = if (enabled) on + sport.name else on
        prefs[Keys.AUTO_PAUSE_OFF] = if (enabled) off else off + sport.name
    }

    suspend fun setAudioCues(cues: AudioCueSettings) = edit {
        it[Keys.AUDIO_ENABLED] = cues.enabled
        it[Keys.AUDIO_INTERVAL] = cues.distanceInterval
        it[Keys.AUDIO_AUTO_PAUSE] = cues.announceAutoPause
    }

    suspend fun setDefaultSport(sport: SportType) = edit { it[Keys.DEFAULT_SPORT] = sport.name }

    suspend fun setDefaultVisibility(visibility: Visibility) = edit { it[Keys.DEFAULT_VISIBILITY] = visibility.name }

    suspend fun setPrivacy(privacy: PrivacySettings) = edit {
        it[Keys.HIDE_START] = privacy.hideStartMeters
        it[Keys.HIDE_END] = privacy.hideEndMeters
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val audioDefaults = AudioCueSettings()
        val privacyDefaults = PrivacySettings()
        val overrides = buildMap {
            prefs[Keys.AUTO_PAUSE_OFF].orEmpty().mapNotNull(::sportOrNull).forEach { put(it, false) }
            prefs[Keys.AUTO_PAUSE_ON].orEmpty().mapNotNull(::sportOrNull).forEach { put(it, true) }
        }
        return AppSettings(
            units = prefs[Keys.UNITS].toEnum<UnitSystem>() ?: AppSettings.defaultUnits(locale()),
            theme = prefs[Keys.THEME].toEnum<ThemePreference>() ?: defaults.theme,
            autoPauseOverrides = overrides,
            audioCues = AudioCueSettings(
                enabled = prefs[Keys.AUDIO_ENABLED] ?: audioDefaults.enabled,
                distanceInterval = prefs[Keys.AUDIO_INTERVAL]?.takeIf { it > 0.0 && it.isFinite() }
                    ?: audioDefaults.distanceInterval,
                announceAutoPause = prefs[Keys.AUDIO_AUTO_PAUSE] ?: audioDefaults.announceAutoPause,
            ),
            defaultSport = prefs[Keys.DEFAULT_SPORT].toEnum<SportType>() ?: defaults.defaultSport,
            defaultVisibility = prefs[Keys.DEFAULT_VISIBILITY].toEnum<Visibility>() ?: defaults.defaultVisibility,
            privacy = PrivacySettings(
                hideStartMeters = prefs[Keys.HIDE_START]?.takeIf { it >= 0.0 } ?: privacyDefaults.hideStartMeters,
                hideEndMeters = prefs[Keys.HIDE_END]?.takeIf { it >= 0.0 } ?: privacyDefaults.hideEndMeters,
            ),
        )
    }

    private fun sportOrNull(name: String): SportType? = name.toEnum<SportType>()

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val AUTO_PAUSE_ON = stringSetPreferencesKey("auto_pause_on")
        val AUTO_PAUSE_OFF = stringSetPreferencesKey("auto_pause_off")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_cues_enabled")
        val AUDIO_INTERVAL = doublePreferencesKey("audio_cues_interval")
        val AUDIO_AUTO_PAUSE = booleanPreferencesKey("audio_cues_auto_pause")
        val DEFAULT_SPORT = stringPreferencesKey("default_sport")
        val DEFAULT_VISIBILITY = stringPreferencesKey("default_visibility")
        val HIDE_START = doublePreferencesKey("privacy_hide_start_m")
        val HIDE_END = doublePreferencesKey("privacy_hide_end_m")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
