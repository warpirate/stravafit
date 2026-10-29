// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.openactivity.feature.record.ui.RecordRoute

/** Full-screen recorder, outside the tab stacks; the app hides its bottom bar here. */
@Serializable
data object RecordDestination

/** Opens the recorder once, however many times the entry point is tapped. */
fun NavController.navigateToRecord(builder: NavOptionsBuilder.() -> Unit = {}) {
    navigate(RecordDestination) {
        launchSingleTop = true
        builder()
    }
}

/**
 * Adds the Record screen, sliding up like a modal.
 *
 * @param onClose closes (idle) or minimises (recording) the screen; usually `popBackStack`.
 * @param onActivitySaved opens the saved activity; Record has already been left by then if the caller pops.
 * @param durationMillis slide duration; pass 0 to honour reduced motion.
 */
fun NavGraphBuilder.recordScreen(
    onClose: () -> Unit,
    onActivitySaved: (Long) -> Unit,
    durationMillis: Int = DEFAULT_SLIDE_MS,
) {
    composable<RecordDestination>(
        enterTransition = { slideInVertically(tween(durationMillis)) { it } },
        exitTransition = { fadeOut(tween(durationMillis / 2)) },
        popExitTransition = { slideOutVertically(tween(durationMillis)) { it } },
    ) {
        // dropUnlessResumed: a double tap during the exit transition must not pop the screen below too.
        RecordRoute(onClose = dropUnlessResumed(block = onClose), onActivitySaved = onActivitySaved)
    }
}

private const val DEFAULT_SLIDE_MS = 420
