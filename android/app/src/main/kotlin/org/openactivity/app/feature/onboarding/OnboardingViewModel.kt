// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.OnboardingChoices
import org.openactivity.app.data.OnboardingRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.util.Locale
import javax.inject.Inject

/** First-run steps, in order. */
enum class OnboardingStep {
    Name,
    Units,
    Sport,
    Privacy,
}

/** State of first-run onboarding; defaults are privacy-first (followers only, 200 m hidden). */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Name,
    val name: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val sport: SportType = SportType.RUN,
    val visibility: Visibility = Visibility.DEFAULT,
    val hideRadiusMeters: Double = PrivacySettings.DEFAULT_HIDE_METERS,
    val isSaving: Boolean = false,
    val finished: Boolean = false,
) {
    val isFirstStep: Boolean get() = step.ordinal == 0
    val isLastStep: Boolean get() = step.ordinal == OnboardingStep.entries.lastIndex
}

/** First-run onboarding: name, units, sport and privacy defaults, stored locally. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repository: OnboardingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState(units = AppSettings.defaultUnits(Locale.getDefault())))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(MAX_NAME_LENGTH)) }

    fun setUnits(units: UnitSystem) = _uiState.update { it.copy(units = units) }

    fun setSport(sport: SportType) = _uiState.update { it.copy(sport = sport) }

    fun setVisibility(visibility: Visibility) = _uiState.update { it.copy(visibility = visibility) }

    fun setHideRadius(meters: Double) = _uiState.update { it.copy(hideRadiusMeters = meters) }

    /** Goes to the next step, or saves everything on the last one. */
    fun next() {
        val state = _uiState.value
        if (state.isLastStep) finish() else _uiState.update { it.copy(step = OnboardingStep.entries[state.step.ordinal + 1]) }
    }

    /** Goes back one step; returns false on the first step so the caller can let the system handle back. */
    fun back(): Boolean {
        val state = _uiState.value
        if (state.isFirstStep) return false
        _uiState.update { it.copy(step = OnboardingStep.entries[state.step.ordinal - 1]) }
        return true
    }

    private fun finish() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.complete(OnboardingChoices(state.name, state.units, state.sport, state.visibility, state.hideRadiusMeters))
            _uiState.update { it.copy(isSaving = false, finished = true) }
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
