// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.openactivity.app.model.UserProfile
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-shell values that live only on this device and are not part of the `:core:datastore`
 * settings: the local profile, first-run state and reduced motion. An unreadable file reads as
 * empty, so a corrupt store never crashes the app.
 */
@Singleton
class LocalPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val data: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    /** Whether celebrations, shimmer and transitions are turned off. */
    val reduceMotion: Flow<Boolean> = data.map { it[Keys.REDUCE_MOTION] ?: false }.distinctUntilChanged()

    /** True once first-run onboarding has been finished. */
    val onboardingComplete: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    /** The local profile shown on the Me tab and on your activity cards. */
    val profile: Flow<UserProfile> = data.map { prefs ->
        UserProfile(
            name = prefs[Keys.NAME].orEmpty(),
            location = prefs[Keys.LOCATION].orEmpty(),
            photoPath = prefs[Keys.PHOTO]?.takeIf { it.isNotBlank() },
        )
    }.distinctUntilChanged()

    /**
     * Home feed "seen" marker: activities with a database id above it have not been seen in the feed
     * yet (ids only grow, so a late import of an old activity still counts as new). Null before the
     * feed was ever shown.
     */
    val feedSeenThroughId: Flow<Long?> = data.map { it[Keys.FEED_SEEN_THROUGH] }.distinctUntilChanged()

    /** Raises the feed "seen" marker to [id]; it never moves back. */
    suspend fun markFeedSeenThrough(id: Long) {
        dataStore.edit { prefs -> if ((prefs[Keys.FEED_SEEN_THROUGH] ?: Long.MIN_VALUE) < id) prefs[Keys.FEED_SEEN_THROUGH] = id }
    }

    /**
     * Id of the newest activity the inbox has looked at for new best efforts, goals and recovered
     * recordings; null before the inbox ran for the first time.
     */
    val inboxWatermark: Flow<Long?> = data.map { it[Keys.INBOX_WATERMARK] }.distinctUntilChanged()

    suspend fun setInboxWatermark(activityId: Long) {
        dataStore.edit { it[Keys.INBOX_WATERMARK] = activityId }
    }

    /** Whether Home and Maps suggest loops near you, sending your position to the routing services. */
    val routeSuggestionsEnabled: Flow<Boolean> = data.map { it[Keys.SUGGESTIONS_ENABLED] ?: true }.distinctUntilChanged()

    /** True once the one-time explanation of online route suggestions was dismissed. */
    val routeSuggestionsIntroSeen: Flow<Boolean> = data.map { it[Keys.SUGGESTIONS_INTRO_SEEN] ?: false }.distinctUntilChanged()

    suspend fun setRouteSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SUGGESTIONS_ENABLED] = enabled }
    }

    suspend fun setRouteSuggestionsIntroSeen() {
        dataStore.edit { it[Keys.SUGGESTIONS_INTRO_SEEN] = true }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setOnboardingComplete(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** Stores name and location, trimmed. */
    suspend fun setProfileDetails(name: String, location: String) {
        dataStore.edit {
            it[Keys.NAME] = name.trim()
            it[Keys.LOCATION] = location.trim()
        }
    }

    /** Stores the absolute path of the profile photo, or clears it with null. */
    suspend fun setPhotoPath(path: String?) {
        dataStore.edit { if (path == null) it.remove(Keys.PHOTO) else it[Keys.PHOTO] = path }
    }

    private object Keys {
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_complete")
        val NAME = stringPreferencesKey("profile_name")
        val LOCATION = stringPreferencesKey("profile_location")
        val PHOTO = stringPreferencesKey("profile_photo_path")
        val FEED_SEEN_THROUGH = longPreferencesKey("feed_seen_through_id")
        val INBOX_WATERMARK = longPreferencesKey("inbox_watermark_activity_id")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("route_suggestions_enabled")
        val SUGGESTIONS_INTRO_SEEN = booleanPreferencesKey("route_suggestions_intro_seen")
    }
}
