// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.core.routing.RouteSuggestions
import javax.inject.Inject
import javax.inject.Singleton

/** The "Online route suggestions" setting and its one-time explanation. */
data class SuggestionSettingsState(val enabled: Boolean = true, val introSeen: Boolean = false)

interface SuggestionSettings {
    val state: Flow<SuggestionSettingsState>

    /** Turning suggestions off also deletes the cached ones. */
    suspend fun setEnabled(enabled: Boolean)

    suspend fun markIntroSeen()
}

@Singleton
class LocalSuggestionSettings @Inject constructor(
    private val local: LocalPreferences,
    private val suggestions: RouteSuggestions,
) : SuggestionSettings {
    override val state: Flow<SuggestionSettingsState> =
        combine(local.routeSuggestionsEnabled, local.routeSuggestionsIntroSeen, ::SuggestionSettingsState)

    override suspend fun setEnabled(enabled: Boolean) {
        local.setRouteSuggestionsEnabled(enabled)
        if (!enabled) suggestions.clear()
        local.setRouteSuggestionsIntroSeen()
    }

    override suspend fun markIntroSeen() = local.setRouteSuggestionsIntroSeen()
}
