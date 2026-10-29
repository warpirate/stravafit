// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Choices made during first-run onboarding.
 *
 * @property hideRadiusMeters radius hidden around the start and end of new activities (0 = off).
 */
data class OnboardingChoices(
    val name: String,
    val units: UnitSystem,
    val sport: SportType,
    val visibility: Visibility,
    val hideRadiusMeters: Double,
)

/** First-run state. */
interface OnboardingRepository {
    /** True once onboarding was finished; the app shows it until then. */
    val isComplete: Flow<Boolean>

    /** Stores [choices] as the user's settings and marks onboarding finished. */
    suspend fun complete(choices: OnboardingChoices)
}

/** [OnboardingRepository] writing to the `:core:data` settings and [LocalPreferences]. */
@Singleton
class LocalOnboardingRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val local: LocalPreferences,
) : OnboardingRepository {

    override val isComplete: Flow<Boolean> = local.onboardingComplete

    override suspend fun complete(choices: OnboardingChoices) {
        local.setProfileDetails(choices.name, location = "")
        settings.setUnits(choices.units)
        settings.setDefaultSport(choices.sport)
        settings.setDefaultVisibility(choices.visibility)
        settings.setPrivacy(PrivacySettings(choices.hideRadiusMeters, choices.hideRadiusMeters))
        // Written last: an interrupted run shows onboarding again instead of half-applied choices.
        local.setOnboardingComplete(true)
    }
}
