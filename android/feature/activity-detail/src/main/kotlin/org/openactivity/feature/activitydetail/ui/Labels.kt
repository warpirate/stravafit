// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import org.openactivity.model.Visibility

/** Icon for [sport]. */
@Suppress("DEPRECATION") // The non-mirrored sport glyphs are the intended look.
internal fun sportIcon(sport: SportType): ImageVector = when {
    sport == SportType.WALK -> Icons.Rounded.DirectionsWalk
    sport == SportType.HIKE -> Icons.Rounded.Hiking
    sport == SportType.SWIM -> Icons.Rounded.Pool
    sport.category == SportCategory.FOOT -> Icons.Rounded.DirectionsRun
    sport.category == SportCategory.BIKE -> Icons.Rounded.DirectionsBike
    sport.category == SportCategory.WATER -> Icons.Rounded.Rowing
    sport.category == SportCategory.SNOW -> Icons.Rounded.DownhillSkiing
    sport.category == SportCategory.FITNESS -> Icons.Rounded.FitnessCenter
    sport.category == SportCategory.RACKET_BALL -> Icons.Rounded.SportsTennis
    else -> Icons.Rounded.Terrain
}

/** "Everyone", "Followers" or "Only me". */
internal fun visibilityLabel(visibility: Visibility): String = when (visibility) {
    Visibility.EVERYONE -> "Everyone"
    Visibility.FOLLOWERS -> "Followers"
    Visibility.ONLY_ME -> "Only me"
}

/** Icon for [visibility]. */
internal fun visibilityIcon(visibility: Visibility): ImageVector = when (visibility) {
    Visibility.EVERYONE -> Icons.Rounded.Public
    Visibility.FOLLOWERS -> Icons.Rounded.Group
    Visibility.ONLY_ME -> Icons.Rounded.Lock
}

/** Short band name of a 1..10 perceived-exertion rating. */
internal fun exertionBand(rpe: Int): String = when (rpe) {
    in 1..3 -> "Easy"
    in 4..6 -> "Moderate"
    in 7..8 -> "Hard"
    else -> "Max effort"
}
