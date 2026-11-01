// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
class AthleteViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val device = userDeviceName(context) ?: modelName()

    val athlete: StateFlow<AthleteIdentity> = profileRepository.profile
        .map { AthleteIdentity(it.displayName, it.location.trim(), it.photoPath, device) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AthleteIdentity(device = device))

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * The name the phone's owner sees in Settings, About phone ("Pixel 8", "Galaxy S23"). Build.MODEL is
         * often an internal code ("SM-S918B"), so it is only the fallback.
         */
        fun userDeviceName(context: Context): String? = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: SecurityException) {
            null
        }

        /** "Google Pixel 8", without repeating the maker when the model already starts with it. */
        fun modelName(): String {
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
