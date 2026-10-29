// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.ProfileRepository
import org.openactivity.core.designsystem.component.AthleteIdentity
import java.util.Locale
import javax.inject.Inject

/** The local profile as the [AthleteIdentity] feature screens show in author rows. */
@HiltViewModel
class AthleteViewModel @Inject constructor(profileRepository: ProfileRepository) : ViewModel() {
    val athlete: StateFlow<AthleteIdentity> = profileRepository.profile
        .map { AthleteIdentity(it.displayName, it.location.trim(), it.photoPath, deviceName()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AthleteIdentity(device = deviceName()))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** "Google Pixel 8", without repeating the maker when the model already starts with it. */
        fun deviceName(): String {
            val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) }
            val model = Build.MODEL.orEmpty()
            return when {
                model.isBlank() -> maker
                model.startsWith(maker, ignoreCase = true) -> model
                else -> "$maker $model"
            }.trim()
        }
    }
}
