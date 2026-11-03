// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openactivity.model.PaceOrSpeed
import org.openactivity.model.SportType
import org.openactivity.model.SwimPaceUnit
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.io.IOException
import java.time.DayOfWeek
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
        it[Keys.AUDIO_STATUS] = cues.announceStatus
        it[Keys.AUDIO_SPLITS] = cues.announceSplits
        it[Keys.AUDIO_LAPS] = cues.announceLaps
        it[Keys.AUDIO_ROUTE] = cues.announceRoute
        it[Keys.AUDIO_SEGMENTS] = cues.announceSegments
        it[Keys.AUDIO_DISTANCE] = cues.distanceCues
        it[Keys.AUDIO_TIME_MINUTES] = cues.timeIntervalMinutes
    }

    /** Distance auto-lap every [meters]; 0 turns it off. */
    suspend fun setAutoLap(meters: Double) = edit { it[Keys.AUTO_LAP_METERS] = meters.coerceAtLeast(0.0) }

    suspend fun setOffRouteAlerts(enabled: Boolean) = edit { it[Keys.OFF_ROUTE_ALERTS] = enabled }

    suspend fun setDefaultSport(sport: SportType) = edit { it[Keys.DEFAULT_SPORT] = sport.name }

    suspend fun setDefaultVisibility(visibility: Visibility) = edit { it[Keys.DEFAULT_VISIBILITY] = visibility.name }

    suspend fun setPrivacy(privacy: PrivacySettings) = edit {
        it[Keys.HIDE_START] = privacy.hideStartMeters
        it[Keys.HIDE_END] = privacy.hideEndMeters
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) = edit { it[Keys.FIRST_DAY_OF_WEEK] = day.name }

    suspend fun setNotifications(notifications: NotificationSettings) = edit {
        it[Keys.NOTIFY_ACHIEVEMENTS] = notifications.achievements
        it[Keys.NOTIFY_GOALS] = notifications.goals
        it[Keys.NOTIFY_WEEKLY] = notifications.weeklySummary
    }

    /** Sets [sport] to show pace or speed; null returns it to the sport's default. */
    suspend fun setSpeedOverride(sport: SportType, choice: PaceOrSpeed?) = edit { prefs ->
        prefs[Keys.SPEED_OVERRIDES] = putEntry(prefs[Keys.SPEED_OVERRIDES], sport, choice?.name)
    }

    /** Per 100 m or per 100 yd for swims; null follows the unit system. */
    suspend fun setSwimPaceUnit(unit: SwimPaceUnit?) = edit { prefs ->
        if (unit == null) prefs.remove(Keys.SWIM_UNIT) else prefs[Keys.SWIM_UNIT] = unit.name
    }

    /** Default visibility for new activities of [sport]; null falls back to the account default. */
    suspend fun setVisibilityOverride(sport: SportType, visibility: Visibility?) = edit { prefs ->
        prefs[Keys.VISIBILITY_OVERRIDES] = putEntry(prefs[Keys.VISIBILITY_OVERRIDES], sport, visibility?.name)
    }

    suspend fun setLaunchTab(tab: LaunchTab) = edit { it[Keys.LAUNCH_TAB] = tab.name }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        val audioDefaults = AudioCueSettings()
        val privacyDefaults = PrivacySettings()
        val notifyDefaults = NotificationSettings()
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
                announceStatus = prefs[Keys.AUDIO_STATUS] ?: audioDefaults.announceStatus,
                announceSplits = prefs[Keys.AUDIO_SPLITS] ?: audioDefaults.announceSplits,
                announceLaps = prefs[Keys.AUDIO_LAPS] ?: audioDefaults.announceLaps,
                announceRoute = prefs[Keys.AUDIO_ROUTE] ?: audioDefaults.announceRoute,
                announceSegments = prefs[Keys.AUDIO_SEGMENTS] ?: audioDefaults.announceSegments,
                // Before per-cue toggles, a custom interval was the only cue; keep it on for those who set one.
                distanceCues = prefs[Keys.AUDIO_DISTANCE]
                    ?: prefs[Keys.AUDIO_INTERVAL]?.let { it != audioDefaults.distanceInterval }
                    ?: audioDefaults.distanceCues,
                timeIntervalMinutes = prefs[Keys.AUDIO_TIME_MINUTES]?.takeIf { it >= 0 } ?: audioDefaults.timeIntervalMinutes,
            ),
            defaultSport = prefs[Keys.DEFAULT_SPORT].toEnum<SportType>() ?: defaults.defaultSport,
            // Fail safe: a stored value this version cannot read becomes the most private choice.
            defaultVisibility = prefs[Keys.DEFAULT_VISIBILITY]?.let { Visibility.parseOrPrivate(it) }
                ?: defaults.defaultVisibility,
            privacy = PrivacySettings(
                hideStartMeters = prefs[Keys.HIDE_START]?.takeIf { it >= 0.0 } ?: privacyDefaults.hideStartMeters,
                hideEndMeters = prefs[Keys.HIDE_END]?.takeIf { it >= 0.0 } ?: privacyDefaults.hideEndMeters,
            ),
            firstDayOfWeek = prefs[Keys.FIRST_DAY_OF_WEEK].toEnum<DayOfWeek>() ?: defaults.firstDayOfWeek,
            notifications = NotificationSettings(
                achievements = prefs[Keys.NOTIFY_ACHIEVEMENTS] ?: notifyDefaults.achievements,
                goals = prefs[Keys.NOTIFY_GOALS] ?: notifyDefaults.goals,
                weeklySummary = prefs[Keys.NOTIFY_WEEKLY] ?: notifyDefaults.weeklySummary,
            ),
            speedOverrides = entries(prefs[Keys.SPEED_OVERRIDES]) { it.toEnum<PaceOrSpeed>() },
            swimPaceUnit = prefs[Keys.SWIM_UNIT].toEnum<SwimPaceUnit>(),
            visibilityOverrides = entries(prefs[Keys.VISIBILITY_OVERRIDES]) { Visibility.parseOrPrivate(it) },
            launchTab = prefs[Keys.LAUNCH_TAB].toEnum<LaunchTab>() ?: defaults.launchTab,
            autoLapMeters = prefs[Keys.AUTO_LAP_METERS]?.takeIf { it >= 0.0 && it.isFinite() } ?: defaults.autoLapMeters,
            offRouteAlerts = prefs[Keys.OFF_ROUTE_ALERTS] ?: defaults.offRouteAlerts,
        )
    }

    private fun sportOrNull(name: String): SportType? = name.toEnum<SportType>()

    /** Per-sport entries stored as "SPORT=VALUE"; unknown sports are dropped, values parsed by [value]. */
    private fun <V : Any> entries(stored: Set<String>?, value: (String) -> V?): Map<SportType, V> = buildMap {
        stored.orEmpty().forEach { entry ->
            val sport = sportOrNull(entry.substringBefore(SEPARATOR, "")) ?: return@forEach
            value(entry.substringAfter(SEPARATOR, ""))?.let { put(sport, it) }
        }
    }

    private fun putEntry(stored: Set<String>?, sport: SportType, value: String?): Set<String> {
        val others = stored.orEmpty().filterNot { it.substringBefore(SEPARATOR, "") == sport.name }.toSet()
        return if (value == null) others else others + "${sport.name}$SEPARATOR$value"
    }

    private object Keys {
        val UNITS = stringPreferencesKey("units")
        val THEME = stringPreferencesKey("theme")
        val AUTO_PAUSE_ON = stringSetPreferencesKey("auto_pause_on")
        val AUTO_PAUSE_OFF = stringSetPreferencesKey("auto_pause_off")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_cues_enabled")
        val AUDIO_INTERVAL = doublePreferencesKey("audio_cues_interval")
        val AUDIO_AUTO_PAUSE = booleanPreferencesKey("audio_cues_auto_pause")
        val AUDIO_STATUS = booleanPreferencesKey("audio_cues_status")
        val AUDIO_SPLITS = booleanPreferencesKey("audio_cues_splits")
        val AUDIO_LAPS = booleanPreferencesKey("audio_cues_laps")
        val AUDIO_ROUTE = booleanPreferencesKey("audio_cues_route")
        val AUDIO_SEGMENTS = booleanPreferencesKey("audio_cues_segments")
        val AUDIO_DISTANCE = booleanPreferencesKey("audio_cues_distance")
        val AUDIO_TIME_MINUTES = intPreferencesKey("audio_cues_time_minutes")
        val AUTO_LAP_METERS = doublePreferencesKey("auto_lap_m")
        val OFF_ROUTE_ALERTS = booleanPreferencesKey("off_route_alerts")
        val DEFAULT_SPORT = stringPreferencesKey("default_sport")
        val DEFAULT_VISIBILITY = stringPreferencesKey("default_visibility")
        val HIDE_START = doublePreferencesKey("privacy_hide_start_m")
        val HIDE_END = doublePreferencesKey("privacy_hide_end_m")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val NOTIFY_ACHIEVEMENTS = booleanPreferencesKey("notify_achievements")
        val NOTIFY_GOALS = booleanPreferencesKey("notify_goals")
        val NOTIFY_WEEKLY = booleanPreferencesKey("notify_weekly_summary")
        val SPEED_OVERRIDES = stringSetPreferencesKey("speed_overrides")
        val SWIM_UNIT = stringPreferencesKey("swim_pace_unit")
        val VISIBILITY_OVERRIDES = stringSetPreferencesKey("visibility_overrides")
        val LAUNCH_TAB = stringPreferencesKey("launch_tab")
    }

    private companion object {
        const val SEPARATOR = '='
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
