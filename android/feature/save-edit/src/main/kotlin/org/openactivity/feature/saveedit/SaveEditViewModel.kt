// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.data.model.ActivityMetadataUpdate
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.model.SportType
import org.openactivity.model.UnitConstants
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the Save screen (right after recording; the activity is already stored with defaults) and the
 * Edit screen. Field changes stay in memory until [save]; photos are stored as soon as they are added.
 *
 * The title follows the sport while it is still the automatic one: changing Run to Ride turns
 * "Morning Run" into "Morning Ride", but a title the athlete typed is never touched.
 */
@HiltViewModel
class SaveEditViewModel internal constructor(
    private val activityId: Long,
    private val isNew: Boolean,
    private val activities: ActivityRepository,
    private val extras: ActivityExtrasRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        activities: ActivityRepository,
        extras: ActivityExtrasRepository,
        settings: SettingsRepository,
    ) : this(
        activityId = checkNotNull(savedStateHandle.get<Long>(ARG_ACTIVITY_ID)) { "activityId missing" },
        isNew = savedStateHandle.get<Boolean>(ARG_IS_NEW) ?: false,
        activities = activities,
        extras = extras,
        settings = settings,
    )

    private val _uiState = MutableStateFlow<SaveEditUiState>(SaveEditUiState.Loading)

    /** Current screen state. */
    val uiState: StateFlow<SaveEditUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<SaveEditEffect>(Channel.BUFFERED)

    /** One-shot effects; collect once from the screen. */
    val effects: Flow<SaveEditEffect> = effectChannel.receiveAsFlow()

    private var stored: SaveEditForm? = null

    /** Set once Close was sent, so a double tap on Back cannot pop the screen below too. */
    private var closing = false
    private var activity: SavedActivity? = null

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            extras.observe(activityId).collect { ex -> editing { it.copy(photos = ex.photos) } }
        }
    }

    private suspend fun load() {
        val a = activities.observeActivity(activityId).first()
        if (a == null) {
            _uiState.value = SaveEditUiState.NotFound
            return
        }
        activity = a
        val ex = extras.get(activityId)
        val units = settings.settings.first().units
        val form = SaveEditForm(
            title = a.title,
            description = a.description,
            sport = a.sport,
            visibility = a.visibility,
            perceivedExertion = a.perceivedExertion,
            muted = ex.muted,
        )
        stored = form
        _uiState.value = SaveEditUiState.Editing(
            isNew = isNew,
            form = form,
            facts = SaveEditFacts(
                defaultTitle = defaultTitle(a, a.sport),
                summary = summary(a, units),
                hideStartMeters = a.hideStartMeters,
                hideEndMeters = a.hideEndMeters,
                hasRoute = a.polyline != null,
            ),
            photos = ex.photos,
            dirty = false,
        )
    }

    /** Sets the title as typed. */
    fun setTitle(title: String) = updateForm { it.copy(title = title.take(MAX_TITLE)) }

    /** Sets the description. */
    fun setDescription(description: String) = updateForm { it.copy(description = description.take(MAX_DESCRIPTION)) }

    /** Changes the sport; an automatic title follows it. */
    fun setSport(sport: SportType) {
        val a = activity ?: return
        editing { state ->
            val form = state.form
            val wasAuto = form.title.isBlank() || form.title.trim() == defaultTitle(a, form.sport)
            val next = form.copy(sport = sport, title = if (wasAuto && form.title.isNotBlank()) defaultTitle(a, sport) else form.title)
            state.copy(
                form = next,
                facts = state.facts.copy(defaultTitle = defaultTitle(a, sport)),
                dirty = next != stored,
                sportPickerOpen = false,
            )
        }
    }

    /** Opens or closes the sport picker. */
    fun setSportPickerOpen(open: Boolean) = editing { it.copy(sportPickerOpen = open) }

    /** Sets who can see the activity. */
    fun setVisibility(visibility: Visibility) = updateForm { it.copy(visibility = visibility) }

    /** Sets the 1..10 rating, or clears it with null. */
    fun setPerceivedExertion(rating: Int?) =
        updateForm { it.copy(perceivedExertion = rating?.coerceIn(PerceivedExertion.MIN, PerceivedExertion.MAX)) }

    /** Keeps the activity out of feeds, or lets it back in. */
    fun setMuted(muted: Boolean) = updateForm { it.copy(muted = muted) }

    /** Stores the photos picked at [uris]. */
    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        editing { it.copy(addingPhotos = true) }
        viewModelScope.launch {
            try {
                val result = extras.addPhotos(activityId, uris)
                val problems = listOfNotNull(
                    "${result.skippedOverLimit} over the limit of ${org.openactivity.core.activityextras.ActivityExtras.MAX_PHOTOS}"
                        .takeIf { result.skippedOverLimit > 0 },
                    "${result.failed} could not be read".takeIf { result.failed > 0 },
                )
                if (problems.isNotEmpty()) effectChannel.send(SaveEditEffect.Message("Some photos were not added: ${problems.joinToString(", ")}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effectChannel.send(SaveEditEffect.Message("Could not add photos: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                editing { it.copy(addingPhotos = false) }
            }
        }
    }

    /** Removes the photo [photoId]. */
    fun removePhoto(photoId: String) {
        viewModelScope.launch { extras.removePhoto(activityId, photoId) }
    }

    /** Stores the form. An empty title becomes the default title. */
    fun save() {
        val state = _uiState.value as? SaveEditUiState.Editing ?: return
        val a = activity ?: return
        if (state.saving) return
        editing { it.copy(saving = true) }
        viewModelScope.launch {
            val form = state.form
            try {
                val title = form.title.trim().ifEmpty { defaultTitle(a, form.sport) }
                val ok = activities.updateMetadata(
                    activityId,
                    ActivityMetadataUpdate(form.sport, title, form.description.trim(), form.visibility, form.perceivedExertion),
                )
                if (!ok) {
                    _uiState.value = SaveEditUiState.NotFound
                    return@launch
                }
                extras.setMuted(activityId, form.muted)
                stored = form.copy(title = title)
                editing { it.copy(saving = false, dirty = false, form = it.form.copy(title = title)) }
                effectChannel.send(SaveEditEffect.Saved(activityId, isNew))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editing { it.copy(saving = false) }
                effectChannel.send(SaveEditEffect.Message("Could not save: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    /** Asks to delete the just-recorded activity. */
    fun requestDiscard() = editing { if (it.isNew) it.copy(dialog = SaveEditDialog.DISCARD_ACTIVITY) else it }

    /** Back pressed: leaves at once when nothing changed, otherwise asks first. */
    fun onBack() {
        val state = _uiState.value
        if (state is SaveEditUiState.Editing && state.dirty) {
            editing { it.copy(dialog = SaveEditDialog.DISCARD_CHANGES) }
        } else {
            close()
        }
    }

    private fun close() {
        if (closing) return
        closing = true
        effectChannel.trySend(SaveEditEffect.Close)
    }

    /** Closes the open confirmation. */
    fun dismissDialog() = editing { it.copy(dialog = null) }

    /** Confirms the open dialog. */
    fun confirmDialog() {
        val state = _uiState.value as? SaveEditUiState.Editing ?: return
        when (state.dialog) {
            SaveEditDialog.DISCARD_ACTIVITY -> discardActivity()
            SaveEditDialog.DISCARD_CHANGES -> {
                editing { it.copy(dialog = null) }
                close()
            }
            null -> Unit
        }
    }

    private fun discardActivity() {
        editing { it.copy(dialog = null, saving = true) }
        viewModelScope.launch {
            try {
                activities.delete(activityId)
                extras.deleteAll(activityId)
                effectChannel.send(SaveEditEffect.Discarded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editing { it.copy(saving = false) }
                effectChannel.send(SaveEditEffect.Message("Could not discard: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    private fun updateForm(change: (SaveEditForm) -> SaveEditForm) = editing { state ->
        val next = change(state.form)
        state.copy(form = next, dirty = next != stored)
    }

    private inline fun editing(crossinline change: (SaveEditUiState.Editing) -> SaveEditUiState.Editing) {
        _uiState.update { if (it is SaveEditUiState.Editing) change(it) else it }
    }

    internal companion object {
        const val ARG_ACTIVITY_ID = "activityId"
        const val ARG_IS_NEW = "isNew"
        const val MAX_TITLE = 100
        const val MAX_DESCRIPTION = 4000

        /** Default title of [a] as [sport], from its local start time in its own time zone. */
        fun defaultTitle(a: SavedActivity, sport: SportType): String =
            DefaultTitles.forActivity(sport, a.startTime.atZone(a.timeZone).toLocalTime())

        /** "5.02 km · 26:14" in [units]. */
        fun summary(a: SavedActivity, units: UnitSystem): String {
            val seconds = a.movingTime.inWholeSeconds
            val time = if (seconds >= 3600) {
                String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
            } else {
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
            }
            if (a.distanceMeters < 1.0) return time
            val distance = if (units == UnitSystem.METRIC) {
                String.format(Locale.getDefault(), "%.2f km", a.distanceMeters / UnitConstants.METERS_PER_KILOMETER)
            } else {
                String.format(Locale.getDefault(), "%.2f mi", a.distanceMeters / UnitConstants.METERS_PER_MILE)
            }
            return "$distance · $time"
        }
    }
}
