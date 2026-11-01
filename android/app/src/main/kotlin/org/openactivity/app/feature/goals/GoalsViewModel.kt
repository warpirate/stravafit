// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.feature.settings.SettingsUiState
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import javax.inject.Inject

/**
 * The goal editor as shown.
 *
 * @property goalId 0 for a new goal.
 * @property metrics the metrics [period] allows, in chip order.
 * @property targetText what the user typed, in [unit].
 * @property error inline message under the target field, null when it is valid or still empty.
 */
data class GoalEditorUi(
    val goalId: Long = 0,
    val period: GoalPeriod = GoalPeriod.WEEK,
    val metric: GoalMetric = GoalMetric.DISTANCE,
    val sport: SportType? = null,
    val targetText: String = "",
    val unit: String = "",
    val error: String? = null,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val metrics: List<GoalMetric> = GoalMetric.entries,
    val sports: List<SportType> = SettingsUiState.SPORTS,
) {
    val isNew: Boolean get() = goalId == 0L
}

/**
 * State of the goals screen.
 *
 * @property editor the open editor sheet, null when closed.
 * @property message one-shot snackbar text, cleared with [GoalsViewModel.messageShown].
 */
data class GoalsUiState(
    val isLoading: Boolean = true,
    val streak: StreakUi? = null,
    val goals: List<GoalItemUi> = emptyList(),
    val editor: GoalEditorUi? = null,
    val message: String? = null,
)

/** Goals: every goal with its progress in the current period, the weekly streak, and the goal editor. */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val goalRepository: GoalRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val draft = MutableStateFlow<Draft?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.observeProgress(),
        goalRepository.observeStreak(),
        preferencesRepository.preferences,
        draft,
        message,
    ) { progress, streak, preferences, draft, message ->
        val units = preferences.unitSystem
        val formatter = UnitFormatter(units, Locale.getDefault())
        GoalsUiState(
            isLoading = false,
            streak = StreakUi.from(streak),
            goals = progress.map { GoalItemUi.from(it, formatter) },
            editor = draft?.toUi(units),
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GoalsUiState())

    init {
        if (savedStateHandle.get<Boolean>(ARG_NEW_GOAL) == true && savedStateHandle.get<Boolean>(KEY_NEW_GOAL_SHOWN) != true) {
            savedStateHandle[KEY_NEW_GOAL_SHOWN] = true
            addGoal()
        }
    }

    /** Opens the editor for a new goal. */
    fun addGoal() {
        draft.value = Draft()
    }

    /** Opens the editor for an existing goal; does nothing when it no longer exists. */
    fun editGoal(id: Long) {
        viewModelScope.launch {
            val goal = goalRepository.getGoal(id) ?: return@launch
            val units = preferencesRepository.preferences.first().unitSystem
            val text = editableText(GoalFormat.editableTarget(goal.metric, goal.target, units))
            draft.value = Draft(
                goalId = goal.id,
                period = goal.period,
                metric = goal.metric,
                sport = goal.sport,
                targetText = text,
                original = Original(goal.metric, units, text, goal.target),
            )
        }
    }

    fun dismissEditor() {
        draft.value = null
    }

    /** Picks the period; a metric the period does not allow (activities outside weekly) falls back to distance. */
    fun setPeriod(period: GoalPeriod) = draft.update { d ->
        d ?: return@update null
        if (d.metric.allowedFor(period)) d.copy(period = period) else d.copy(period = period, metric = GoalMetric.DISTANCE, targetText = "")
    }

    /** Picks the metric. The target is cleared when the unit changes, since the old number means something else. */
    fun setMetric(metric: GoalMetric) = draft.update { d ->
        when {
            d == null -> null
            !metric.allowedFor(d.period) || metric == d.metric -> d
            else -> d.copy(metric = metric, targetText = "")
        }
    }

    fun setSport(sport: SportType?) = draft.update { it?.copy(sport = sport) }

    fun setTarget(text: String) = draft.update { it?.copy(targetText = text.take(MAX_TARGET_CHARS)) }

    /** Saves the goal in the editor and closes it; does nothing while the target is invalid. */
    fun save() {
        val d = draft.value ?: return
        if (d.saving) return
        val shown = (validate(d.targetText, d.metric) as? Validation.Valid)?.value ?: return
        draft.value = d.copy(saving = true)
        viewModelScope.launch {
            val units = preferencesRepository.preferences.first().unitSystem
            val original = d.original
            // An untouched target keeps its exact stored value, so opening and saving a goal never drifts it
            // through the rounding of the shown number.
            val target = if (original != null && original.metric == d.metric && original.units == units && original.text == d.targetText.trim()) {
                original.target
            } else {
                GoalFormat.storedTarget(d.metric, shown, units)
            }
            goalRepository.save(Goal(id = d.goalId, sport = d.sport, period = d.period, metric = d.metric, target = target))
            draft.value = null
            message.value = if (d.goalId == 0L) "Goal added" else "Goal saved"
        }
    }

    /** Deletes the goal open in the editor and closes it. */
    fun delete() {
        val d = draft.value ?: return
        if (d.goalId == 0L) return
        viewModelScope.launch {
            goalRepository.delete(d.goalId)
            draft.value = null
            message.value = "Goal deleted"
        }
    }

    fun messageShown() {
        message.value = null
    }

    private fun Draft.toUi(units: UnitSystem): GoalEditorUi {
        val validation = validate(targetText, metric)
        return GoalEditorUi(
            goalId = goalId,
            period = period,
            metric = metric,
            sport = sport,
            targetText = targetText,
            unit = GoalFormat.editorUnit(metric, units),
            error = (validation as? Validation.Invalid)?.message,
            canSave = validation is Validation.Valid && !saving,
            isSaving = saving,
            metrics = GoalMetric.entries.filter { it.allowedFor(period) },
        )
    }

    private data class Original(val metric: GoalMetric, val units: UnitSystem, val text: String, val target: Double)

    private data class Draft(
        val goalId: Long = 0,
        val period: GoalPeriod = GoalPeriod.WEEK,
        val metric: GoalMetric = GoalMetric.DISTANCE,
        val sport: SportType? = null,
        val targetText: String = "",
        val original: Original? = null,
        val saving: Boolean = false,
    )

    /** Result of checking a typed target. */
    sealed interface Validation {
        /** Nothing typed yet: Save stays off, no error shown. */
        data object Empty : Validation

        data class Invalid(val message: String) : Validation

        /** [value] is in the editor's unit. */
        data class Valid(val value: Double) : Validation
    }

    internal companion object {
        /** Navigation argument of `GoalsRoute`. */
        const val ARG_NEW_GOAL = "newGoal"
        private const val KEY_NEW_GOAL_SHOWN = "newGoalShown"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MAX_TARGET_CHARS = 9
        private const val EDITABLE_DECIMALS = 2

        /** Checks a typed target: a positive, finite number, and a whole one for activities. Accepts "," as decimal mark. */
        fun validate(text: String, metric: GoalMetric): Validation {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Validation.Empty
            val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return Validation.Invalid("Enter a number")
            return when {
                !value.isFinite() -> Validation.Invalid("Enter a number")
                value <= 0.0 -> Validation.Invalid("Enter a target above 0")
                metric == GoalMetric.COUNT && value != Math.floor(value) -> Validation.Invalid("Use a whole number of activities")
                else -> Validation.Valid(value)
            }
        }

        /** "30", "18.64", "2.5": at most two decimals, no trailing zeros. */
        fun editableText(value: Double): String =
            BigDecimal.valueOf(value).setScale(EDITABLE_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
