// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DownhillSkiing
import androidx.compose.material.icons.rounded.ElectricBike
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.IceSkating
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.Kitesurfing
import androidx.compose.material.icons.rounded.NordicWalking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.RollerSkating
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Skateboarding
import androidx.compose.material.icons.rounded.Snowboarding
import androidx.compose.material.icons.rounded.Snowshoeing
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsGolf
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.Surfing
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType

/** Readable sport name, e.g. "Trail Run". */
internal val SportType.label: String get() = DefaultTitles.sportName(this)

/** Material Symbols glyph for the sport (Apache-2.0). */
internal val SportType.icon: ImageVector
    get() = when (this) {
        SportType.RUN, SportType.VIRTUAL_RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
        SportType.TRAIL_RUN -> Icons.Rounded.Terrain
        SportType.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        SportType.HIKE -> Icons.Rounded.Hiking
        SportType.SNOWSHOE -> Icons.Rounded.Snowshoeing
        SportType.E_BIKE_RIDE, SportType.E_MOUNTAIN_BIKE_RIDE -> Icons.Rounded.ElectricBike
        SportType.SWIM -> Icons.Rounded.Pool
        SportType.ROWING, SportType.VIRTUAL_ROW -> Icons.Rounded.Rowing
        SportType.CANOEING, SportType.KAYAKING, SportType.STAND_UP_PADDLING -> Icons.Rounded.Kayaking
        SportType.SURFING -> Icons.Rounded.Surfing
        SportType.KITESURF, SportType.WINDSURF -> Icons.Rounded.Kitesurfing
        SportType.SAIL -> Icons.Rounded.Sailing
        SportType.ALPINE_SKI, SportType.BACKCOUNTRY_SKI -> Icons.Rounded.DownhillSkiing
        SportType.NORDIC_SKI, SportType.ROLLER_SKI -> Icons.Rounded.NordicWalking
        SportType.SNOWBOARD -> Icons.Rounded.Snowboarding
        SportType.ICE_SKATE -> Icons.Rounded.IceSkating
        SportType.INLINE_SKATE -> Icons.Rounded.RollerSkating
        SportType.SKATEBOARD -> Icons.Rounded.Skateboarding
        SportType.WHEELCHAIR -> Icons.AutoMirrored.Rounded.Accessible
        SportType.WEIGHT_TRAINING, SportType.CROSSFIT -> Icons.Rounded.FitnessCenter
        SportType.YOGA, SportType.PILATES, SportType.PHYSICAL_THERAPY -> Icons.Rounded.SelfImprovement
        SportType.TENNIS, SportType.BADMINTON, SportType.PICKLEBALL, SportType.PADEL, SportType.SQUASH,
        SportType.RACQUETBALL, SportType.TABLE_TENNIS,
        -> Icons.Rounded.SportsTennis
        SportType.BASKETBALL -> Icons.Rounded.SportsBasketball
        SportType.CRICKET -> Icons.Rounded.SportsCricket
        SportType.SOCCER -> Icons.Rounded.SportsSoccer
        SportType.VOLLEYBALL -> Icons.Rounded.SportsVolleyball
        SportType.GOLF -> Icons.Rounded.SportsGolf
        SportType.OTHER, SportType.ROCK_CLIMBING -> Icons.Rounded.Sports
        else -> when (category) {
            SportCategory.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
            SportCategory.FITNESS -> Icons.Rounded.SportsGymnastics
            else -> Icons.Rounded.Sports
        }
    }

/** Section title of a sport category in the picker. */
internal val SportCategory.label: String
    get() = when (this) {
        SportCategory.FOOT -> "Foot sports"
        SportCategory.BIKE -> "Cycle sports"
        SportCategory.WATER -> "Water sports"
        SportCategory.SNOW -> "Winter sports"
        SportCategory.WHEELS -> "Wheel sports"
        SportCategory.FITNESS -> "Fitness"
        SportCategory.RACKET_BALL -> "Racket and ball"
        SportCategory.OTHER -> "Other"
    }

/** Sports offered for live recording: virtual sessions come from apps and files, not the phone. */
internal val recordableSports: List<SportType> = SportType.entries.filter { !it.isVirtual }
