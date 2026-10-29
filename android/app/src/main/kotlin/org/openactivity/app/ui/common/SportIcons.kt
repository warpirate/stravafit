// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ElectricBike
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import org.openactivity.app.model.Sport

/** Material Symbols glyph for each sport (Apache-2.0). Original sport glyphs come later. */
val Sport.icon: ImageVector
    get() = when (this) {
        Sport.Run -> Icons.Rounded.DirectionsRun
        Sport.TrailRun -> Icons.Rounded.Terrain
        Sport.Walk -> Icons.Rounded.DirectionsWalk
        Sport.Hike -> Icons.Rounded.Hiking
        Sport.Ride, Sport.MountainBikeRide, Sport.GravelRide -> Icons.Rounded.DirectionsBike
        Sport.EBikeRide -> Icons.Rounded.ElectricBike
        Sport.Swim -> Icons.Rounded.Pool
        Sport.Rowing -> Icons.Rounded.Rowing
        Sport.WeightTraining -> Icons.Rounded.FitnessCenter
        Sport.Yoga -> Icons.Rounded.SelfImprovement
        Sport.Workout -> Icons.Rounded.SportsGymnastics
    }
