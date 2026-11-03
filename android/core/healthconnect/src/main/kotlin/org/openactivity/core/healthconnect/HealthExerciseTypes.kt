// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_BADMINTON
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_BASKETBALL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_BIKING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_BIKING_STATIONARY
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_BOOT_CAMP
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_CALISTHENICS
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_CRICKET
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_DANCING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_ELLIPTICAL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_EXERCISE_CLASS
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_GOLF
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_HIKING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_ICE_SKATING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_OTHER_WORKOUT
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_PADDLING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_PILATES
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_RACQUETBALL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_ROCK_CLIMBING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_ROWING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_ROWING_MACHINE
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_RUNNING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_RUNNING_TREADMILL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SAILING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SKATING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SKIING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SNOWBOARDING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SNOWSHOEING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SOCCER
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SQUASH
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_STAIR_CLIMBING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_STRENGTH_TRAINING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_STRETCHING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SURFING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SWIMMING_OPEN_WATER
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_SWIMMING_POOL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_TABLE_TENNIS
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_TENNIS
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_VOLLEYBALL
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_WALKING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_WEIGHTLIFTING
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_WHEELCHAIR
import androidx.health.connect.client.records.ExerciseSessionRecord.Companion.EXERCISE_TYPE_YOGA
import org.openactivity.model.SportType

/**
 * Maps our sports onto Health Connect exercise types and back. Health Connect has fewer, broader
 * types (one "biking" for every kind of ride, one "skiing"), so the way out loses detail and the way
 * back picks the most common of our sports for each type. Types we have no sport for become a workout.
 */
public object HealthExerciseTypes {

    /** The exercise type to write for [sport]; [outdoors] tells an open-water swim from a pool swim. */
    public fun toHealth(sport: SportType, outdoors: Boolean): Int = when (sport) {
        SportType.RUN, SportType.TRAIL_RUN -> EXERCISE_TYPE_RUNNING
        SportType.VIRTUAL_RUN -> EXERCISE_TYPE_RUNNING_TREADMILL
        SportType.WALK -> EXERCISE_TYPE_WALKING
        SportType.HIKE -> EXERCISE_TYPE_HIKING
        SportType.SNOWSHOE -> EXERCISE_TYPE_SNOWSHOEING
        SportType.RIDE, SportType.MOUNTAIN_BIKE_RIDE, SportType.GRAVEL_RIDE, SportType.E_BIKE_RIDE,
        SportType.E_MOUNTAIN_BIKE_RIDE, SportType.VELOMOBILE, SportType.HANDCYCLE,
        -> EXERCISE_TYPE_BIKING
        SportType.VIRTUAL_RIDE -> EXERCISE_TYPE_BIKING_STATIONARY
        SportType.SWIM -> if (outdoors) EXERCISE_TYPE_SWIMMING_OPEN_WATER else EXERCISE_TYPE_SWIMMING_POOL
        SportType.ROWING -> EXERCISE_TYPE_ROWING
        SportType.VIRTUAL_ROW -> EXERCISE_TYPE_ROWING_MACHINE
        SportType.CANOEING, SportType.KAYAKING, SportType.STAND_UP_PADDLING -> EXERCISE_TYPE_PADDLING
        SportType.SURFING -> EXERCISE_TYPE_SURFING
        SportType.SAIL -> EXERCISE_TYPE_SAILING
        SportType.ALPINE_SKI, SportType.BACKCOUNTRY_SKI, SportType.NORDIC_SKI -> EXERCISE_TYPE_SKIING
        SportType.SNOWBOARD -> EXERCISE_TYPE_SNOWBOARDING
        SportType.ICE_SKATE -> EXERCISE_TYPE_ICE_SKATING
        SportType.INLINE_SKATE -> EXERCISE_TYPE_SKATING
        SportType.WHEELCHAIR -> EXERCISE_TYPE_WHEELCHAIR
        SportType.WEIGHT_TRAINING -> EXERCISE_TYPE_STRENGTH_TRAINING
        SportType.CROSSFIT, SportType.HIGH_INTENSITY_INTERVAL_TRAINING -> EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        SportType.ELLIPTICAL -> EXERCISE_TYPE_ELLIPTICAL
        SportType.STAIR_STEPPER -> EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        SportType.YOGA -> EXERCISE_TYPE_YOGA
        SportType.PILATES -> EXERCISE_TYPE_PILATES
        SportType.DANCE -> EXERCISE_TYPE_DANCING
        SportType.BADMINTON -> EXERCISE_TYPE_BADMINTON
        SportType.RACQUETBALL -> EXERCISE_TYPE_RACQUETBALL
        SportType.SQUASH -> EXERCISE_TYPE_SQUASH
        SportType.TABLE_TENNIS -> EXERCISE_TYPE_TABLE_TENNIS
        SportType.TENNIS -> EXERCISE_TYPE_TENNIS
        SportType.BASKETBALL -> EXERCISE_TYPE_BASKETBALL
        SportType.CRICKET -> EXERCISE_TYPE_CRICKET
        SportType.SOCCER -> EXERCISE_TYPE_SOCCER
        SportType.VOLLEYBALL -> EXERCISE_TYPE_VOLLEYBALL
        SportType.GOLF -> EXERCISE_TYPE_GOLF
        SportType.ROCK_CLIMBING -> EXERCISE_TYPE_ROCK_CLIMBING
        // No Health Connect type of their own.
        SportType.KITESURF, SportType.WINDSURF, SportType.ROLLER_SKI, SportType.SKATEBOARD, SportType.WORKOUT,
        SportType.PHYSICAL_THERAPY, SportType.PICKLEBALL, SportType.PADEL, SportType.OTHER,
        -> EXERCISE_TYPE_OTHER_WORKOUT
    }

    /** Our sport for a Health Connect exercise type. */
    public fun fromHealth(type: Int): SportType = when (type) {
        EXERCISE_TYPE_RUNNING -> SportType.RUN
        EXERCISE_TYPE_RUNNING_TREADMILL -> SportType.VIRTUAL_RUN
        EXERCISE_TYPE_WALKING -> SportType.WALK
        EXERCISE_TYPE_HIKING -> SportType.HIKE
        EXERCISE_TYPE_SNOWSHOEING -> SportType.SNOWSHOE
        EXERCISE_TYPE_BIKING -> SportType.RIDE
        EXERCISE_TYPE_BIKING_STATIONARY -> SportType.VIRTUAL_RIDE
        EXERCISE_TYPE_SWIMMING_OPEN_WATER, EXERCISE_TYPE_SWIMMING_POOL -> SportType.SWIM
        EXERCISE_TYPE_ROWING -> SportType.ROWING
        EXERCISE_TYPE_ROWING_MACHINE -> SportType.VIRTUAL_ROW
        EXERCISE_TYPE_PADDLING -> SportType.KAYAKING
        EXERCISE_TYPE_SURFING -> SportType.SURFING
        EXERCISE_TYPE_SAILING -> SportType.SAIL
        EXERCISE_TYPE_SKIING -> SportType.ALPINE_SKI
        EXERCISE_TYPE_SNOWBOARDING -> SportType.SNOWBOARD
        EXERCISE_TYPE_ICE_SKATING -> SportType.ICE_SKATE
        EXERCISE_TYPE_SKATING -> SportType.INLINE_SKATE
        EXERCISE_TYPE_WHEELCHAIR -> SportType.WHEELCHAIR
        EXERCISE_TYPE_STRENGTH_TRAINING, EXERCISE_TYPE_WEIGHTLIFTING -> SportType.WEIGHT_TRAINING
        EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> SportType.HIGH_INTENSITY_INTERVAL_TRAINING
        EXERCISE_TYPE_ELLIPTICAL -> SportType.ELLIPTICAL
        EXERCISE_TYPE_STAIR_CLIMBING, EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> SportType.STAIR_STEPPER
        EXERCISE_TYPE_YOGA -> SportType.YOGA
        EXERCISE_TYPE_PILATES -> SportType.PILATES
        EXERCISE_TYPE_DANCING -> SportType.DANCE
        EXERCISE_TYPE_BADMINTON -> SportType.BADMINTON
        EXERCISE_TYPE_RACQUETBALL -> SportType.RACQUETBALL
        EXERCISE_TYPE_SQUASH -> SportType.SQUASH
        EXERCISE_TYPE_TABLE_TENNIS -> SportType.TABLE_TENNIS
        EXERCISE_TYPE_TENNIS -> SportType.TENNIS
        EXERCISE_TYPE_BASKETBALL -> SportType.BASKETBALL
        EXERCISE_TYPE_CRICKET -> SportType.CRICKET
        EXERCISE_TYPE_SOCCER -> SportType.SOCCER
        EXERCISE_TYPE_VOLLEYBALL -> SportType.VOLLEYBALL
        EXERCISE_TYPE_GOLF -> SportType.GOLF
        EXERCISE_TYPE_ROCK_CLIMBING -> SportType.ROCK_CLIMBING
        EXERCISE_TYPE_CALISTHENICS, EXERCISE_TYPE_BOOT_CAMP, EXERCISE_TYPE_EXERCISE_CLASS, EXERCISE_TYPE_STRETCHING,
        EXERCISE_TYPE_OTHER_WORKOUT,
        -> SportType.WORKOUT
        else -> SportType.WORKOUT
    }
}
