// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.NotificationSettings
import org.openactivity.core.datastore.LaunchTab
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.SettingsDataSource
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.PaceOrSpeed
import org.openactivity.model.SportType
import org.openactivity.model.SwimPaceUnit
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/** User settings. Stored on the device; never synced without the user signing in to a server. */
interface SettingsRepository {
    /** Current settings; emits again on every change. */
    val settings: Flow<AppSettings>

    suspend fun setUnits(units: UnitSystem)

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setAutoPause(sport: SportType, enabled: Boolean)

    suspend fun setAudioCues(cues: AudioCueSettings)

    /** Sport preselected on the Record screen; the recording service sets it on every start. */
    suspend fun setDefaultSport(sport: SportType)

    suspend fun setDefaultVisibility(visibility: Visibility)

    suspend fun setPrivacy(privacy: PrivacySettings)

    /** Where weeks start for weekly totals, weekly goals and the streak. */
    suspend fun setFirstDayOfWeek(day: DayOfWeek)

    /** Which events also post an Android notification. */
    suspend fun setNotifications(notifications: NotificationSettings)
    /** Pace or speed for [sport]; null returns the sport to its default. */
    suspend fun setSpeedOverride(sport: SportType, choice: PaceOrSpeed?)

    /** Per 100 m or per 100 yd for swims; null follows the unit system. */
    suspend fun setSwimPaceUnit(unit: SwimPaceUnit?)

    /** Default visibility for new activities of [sport]; null uses the account default. */
    suspend fun setVisibilityOverride(sport: SportType, visibility: Visibility?)

    /** The tab a cold launch opens on. */
    suspend fun setLaunchTab(tab: LaunchTab)
    /** Distance auto-lap every [meters]; 0 turns it off. */
    suspend fun setAutoLap(meters: Double)

    /** Banner, buzz and spoken alert when leaving a followed route. */
    suspend fun setOffRouteAlerts(enabled: Boolean)
}

/** [SettingsRepository] over DataStore. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val source: SettingsDataSource,
) : SettingsRepository {
    override val settings: Flow<AppSettings> get() = source.settings

    override suspend fun setUnits(units: UnitSystem) = source.setUnits(units)

    override suspend fun setTheme(theme: ThemePreference) = source.setTheme(theme)

    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) = source.setAutoPause(sport, enabled)

    override suspend fun setAudioCues(cues: AudioCueSettings) = source.setAudioCues(cues)

    override suspend fun setDefaultSport(sport: SportType) = source.setDefaultSport(sport)

    override suspend fun setDefaultVisibility(visibility: Visibility) = source.setDefaultVisibility(visibility)

    override suspend fun setPrivacy(privacy: PrivacySettings) = source.setPrivacy(privacy)

    override suspend fun setFirstDayOfWeek(day: DayOfWeek) = source.setFirstDayOfWeek(day)

    override suspend fun setNotifications(notifications: NotificationSettings) = source.setNotifications(notifications)
    override suspend fun setSpeedOverride(sport: SportType, choice: PaceOrSpeed?) = source.setSpeedOverride(sport, choice)

    override suspend fun setSwimPaceUnit(unit: SwimPaceUnit?) = source.setSwimPaceUnit(unit)

    override suspend fun setVisibilityOverride(sport: SportType, visibility: Visibility?) =
        source.setVisibilityOverride(sport, visibility)

    override suspend fun setLaunchTab(tab: LaunchTab) = source.setLaunchTab(tab)
    override suspend fun setAutoLap(meters: Double) = source.setAutoLap(meters)

    override suspend fun setOffRouteAlerts(enabled: Boolean) = source.setOffRouteAlerts(enabled)
}
