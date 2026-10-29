// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.ElectricBike
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.ui.graphics.vector.ImageVector
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType

/** Readable sport name, e.g. "Trail Run". */
val SportType.label: String get() = DefaultTitles.sportName(this)

/** Material Symbols glyph for a sport (Apache-2.0). */
val SportType.icon: ImageVector
    get() = when (this) {
        SportType.RUN, SportType.VIRTUAL_RUN -> Icons.Rounded.DirectionsRun
        SportType.TRAIL_RUN -> Icons.Rounded.Terrain
        SportType.WALK, SportType.SNOWSHOE -> Icons.Rounded.DirectionsWalk
        SportType.HIKE -> Icons.Rounded.Hiking
        SportType.E_BIKE_RIDE, SportType.E_MOUNTAIN_BIKE_RIDE -> Icons.Rounded.ElectricBike
        SportType.SWIM -> Icons.Rounded.Pool
        SportType.ROWING, SportType.VIRTUAL_ROW -> Icons.Rounded.Rowing
        SportType.WEIGHT_TRAINING, SportType.CROSSFIT -> Icons.Rounded.FitnessCenter
        SportType.YOGA, SportType.PILATES -> Icons.Rounded.SelfImprovement
        SportType.WHEELCHAIR -> Icons.Rounded.Accessible
        else -> when (category) {
            SportCategory.BIKE -> Icons.Rounded.DirectionsBike
            SportCategory.FOOT -> Icons.Rounded.DirectionsRun
            SportCategory.WATER -> Icons.Rounded.Kayaking
            SportCategory.SNOW -> Icons.Rounded.DownhillSkiing
            SportCategory.RACKET_BALL -> Icons.Rounded.SportsTennis
            else -> Icons.Rounded.SportsGymnastics
        }
    }
