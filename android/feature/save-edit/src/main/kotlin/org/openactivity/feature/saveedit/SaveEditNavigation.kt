// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.openactivity.feature.saveedit.ui.SaveEditScreen

/**
 * Save (after recording, [isNew] true) or Edit screen of the stored activity [activityId].
 */
@Serializable
data class SaveEditRoute(val activityId: Long, val isNew: Boolean)

/** Opens the Save screen for a just-finished recording that was stored as [activityId]. */
fun NavController.navigateToSaveActivity(activityId: Long, builder: NavOptionsBuilder.() -> Unit = { launchSingleTop = true }) {
    navigate(SaveEditRoute(activityId, isNew = true), builder)
}

/** Opens the Edit screen of [activityId]. */
fun NavController.navigateToEditActivity(activityId: Long) {
    navigate(SaveEditRoute(activityId, isNew = false)) { launchSingleTop = true }
}

/**
 * Where the save / edit screen goes next.
 *
 * @property onSaved changes were stored; [isNew] tells a first save (usually: open the detail) from an edit (usually: go back).
 * @property onDiscarded the just-recorded activity was deleted.
 * @property onClose leave without saving (Back).
 */
data class SaveEditNavigation(
    val onSaved: (activityId: Long, isNew: Boolean) -> Unit,
    val onDiscarded: () -> Unit,
    val onClose: () -> Unit,
)

/** Adds the save / edit destination. */
fun NavGraphBuilder.saveEditScreen(navigation: SaveEditNavigation) {
    composable<SaveEditRoute> {
        SaveEditScreen(navigation = navigation)
    }
}
