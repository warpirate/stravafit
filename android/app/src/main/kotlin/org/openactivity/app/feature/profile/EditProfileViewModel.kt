// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.ProfileRepository
import java.io.IOException
import javax.inject.Inject

/**
 * State of the Edit profile screen.
 *
 * @property saved true once the details were stored; the screen then closes.
 * @property error one-shot message, cleared with [EditProfileViewModel.errorShown].
 */
data class EditProfileUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val location: String = "",
    val photoPath: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Name typed so far, or the default shown on the avatar. */
    val avatarName: String get() = name.trim().ifEmpty { "You" }

    /** Saving needs a name that is not too long. */
    val canSave: Boolean get() = !isLoading && !isSaving && name.trim().length <= MAX_NAME_LENGTH && location.length <= MAX_LOCATION_LENGTH

    companion object {
        const val MAX_NAME_LENGTH: Int = 60
        const val MAX_LOCATION_LENGTH: Int = 80
    }
}

/** Edits the local profile. Photo changes apply immediately; name and location on Save. */
@HiltViewModel
class EditProfileViewModel @Inject constructor(private val repository: ProfileRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = repository.profile.first()
            _uiState.update { it.copy(isLoading = false, name = profile.name, location = profile.location, photoPath = profile.photoPath) }
            // Keep the photo in sync; typed text is never overwritten.
            repository.profile.collect { p -> _uiState.update { it.copy(photoPath = p.photoPath) } }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(EditProfileUiState.MAX_NAME_LENGTH)) }

    fun setLocation(location: String) = _uiState.update { it.copy(location = location.take(EditProfileUiState.MAX_LOCATION_LENGTH)) }

    /** Copies the picked image in as the new profile photo. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.setPhoto(uri)
            } catch (e: IOException) {
                _uiState.update { it.copy(error = "That image could not be used. Try another one.") }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch { repository.removePhoto() }
    }

    /** Stores name and location. */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.updateDetails(state.name, state.location)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun errorShown() = _uiState.update { it.copy(error = null) }
}
