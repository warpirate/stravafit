// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openactivity.model.SportType
import javax.inject.Inject
import javax.inject.Singleton

/** Stores [RecordPreferences]. */
interface RecordPreferencesRepository {
    /** Current preferences; emits again on every change. */
    val preferences: Flow<RecordPreferences>

    suspend fun setCountdown(enabled: Boolean)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setPaceMode(mode: PaceMode)

    /** Moves [sport] to the front of the recent list. */
    suspend fun addRecentSport(sport: SportType)
}

/** [RecordPreferencesRepository] over a private DataStore file. */
@Singleton
class DataStoreRecordPreferencesRepository @Inject constructor(
    @RecordDataStore private val store: DataStore<Preferences>,
) : RecordPreferencesRepository {

    override val preferences: Flow<RecordPreferences> = store.data.map { p ->
        RecordPreferences(
            countdown = p[COUNTDOWN] ?: false,
            keepScreenOn = p[KEEP_SCREEN_ON] ?: false,
            recentSports = p[RECENT].orEmpty().split(SEPARATOR).mapNotNull(SportType::fromApiName),
            paceMode = p[PACE_MODE]?.let { name -> PaceMode.entries.firstOrNull { it.name == name } } ?: PaceMode.SPLIT,
        )
    }

    override suspend fun setCountdown(enabled: Boolean) {
        store.edit { it[COUNTDOWN] = enabled }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    override suspend fun setPaceMode(mode: PaceMode) {
        store.edit { it[PACE_MODE] = mode.name }
    }

    override suspend fun addRecentSport(sport: SportType) {
        store.edit { p ->
            val current = p[RECENT].orEmpty().split(SEPARATOR).mapNotNull(SportType::fromApiName)
            p[RECENT] = withRecent(current, sport).joinToString(SEPARATOR) { it.apiName }
        }
    }

    internal companion object {
        private val COUNTDOWN = booleanPreferencesKey("countdown")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val RECENT = stringPreferencesKey("recent_sports")
        private val PACE_MODE = stringPreferencesKey("pace_mode")
        private const val SEPARATOR = ","

        /** [sport] first, then the others in order, without duplicates, capped at [RecordPreferences.MAX_RECENT]. */
        fun withRecent(current: List<SportType>, sport: SportType): List<SportType> =
            (listOf(sport) + current).distinct().take(RecordPreferences.MAX_RECENT)
    }
}
