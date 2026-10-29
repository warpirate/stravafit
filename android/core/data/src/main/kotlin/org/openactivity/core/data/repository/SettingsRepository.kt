// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.SettingsDataSource
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
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
}
