// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import androidx.compose.runtime.Immutable
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.model.SportType
import org.openactivity.model.Visibility

/**
 * The editable fields of the save and edit screens.
 *
 * @property title the typed title; an empty title saves as the default "<part of day> <sport>".
 * @property perceivedExertion 1..10, or null when not rated.
 * @property muted keep the activity out of home and club feeds.
 */
@Immutable
data class SaveEditForm(
    val title: String,
    val description: String,
    val sport: SportType,
    val visibility: Visibility,
    val perceivedExertion: Int?,
    val muted: Boolean,
)

/**
 * Read-only facts shown on the screen.
 *
 * @property defaultTitle the title an empty field saves as, for the field's placeholder.
 * @property summary e.g. "5.02 km · 26:14", or null for activities without distance.
 * @property hideStartMeters radius hidden around the start from other people (0 when off).
 */
@Immutable
data class SaveEditFacts(
    val defaultTitle: String,
    val summary: String,
    val hideStartMeters: Double,
    val hideEndMeters: Double,
    val hasRoute: Boolean,
)

/** State of the save / edit screen. */
sealed interface SaveEditUiState {
    /** Reading the activity. */
    data object Loading : SaveEditUiState

    /** The activity no longer exists. */
    data object NotFound : SaveEditUiState

    /**
     * Editing.
     *
     * @property isNew true right after recording (Save screen, offers Discard); false for Edit.
     * @property dirty true when [form] differs from what is stored.
     * @property photos stored photos (added and removed immediately).
     * @property dialog open confirmation, if any.
     */
    data class Editing(
        val isNew: Boolean,
        val form: SaveEditForm,
        val facts: SaveEditFacts,
        val photos: List<ActivityPhoto>,
        val dirty: Boolean,
        val saving: Boolean = false,
        val addingPhotos: Boolean = false,
        val dialog: SaveEditDialog? = null,
        val sportPickerOpen: Boolean = false,
    ) : SaveEditUiState
}

/** Confirmations the screen can show. */
enum class SaveEditDialog {
    /** Delete the just-recorded activity for good. */
    DISCARD_ACTIVITY,

    /** Leave without saving changed fields. */
    DISCARD_CHANGES,
}

/** One-shot effects. */
sealed interface SaveEditEffect {
    /** Changes were stored for [activityId]. */
    data class Saved(val activityId: Long) : SaveEditEffect

    /** The just-recorded activity was deleted. */
    data object Discarded : SaveEditEffect

    /** Leave without saving. */
    data object Close : SaveEditEffect

    /** Show [message]. */
    data class Message(val message: String) : SaveEditEffect
}
