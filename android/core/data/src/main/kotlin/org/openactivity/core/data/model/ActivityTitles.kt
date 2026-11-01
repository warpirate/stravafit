// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.model

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.core.data.R
import org.openactivity.model.SportType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Part of the day an activity started in, which names it ("Morning Run"). */
enum class PartOfDay {
    /** 05:00 to 11:59. */
    MORNING,

    /** 12:00 to 16:59. */
    AFTERNOON,

    /** 17:00 to 20:59. */
    EVENING,

    /** 21:00 to 04:59. */
    NIGHT,
    ;

    companion object {
        /** The part of day of a local clock [time] (save-edit-activity S1). */
        fun of(time: LocalTime): PartOfDay = when (time.hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * Automatic activity titles such as "Morning Run", in the user's language.
 *
 * The part of the day comes from the activity's own start time in its own time zone, never the phone's
 * current zone: a run started at 07:00 in Lisbon stays a "Morning Run" when it is viewed in Tokyo.
 */
fun interface ActivityTitles {
    /** Title for [sport] started at local clock [time]. */
    fun forActivity(sport: SportType, time: LocalTime): String

    /** Title for [sport] started at [start], read in the activity's time [zone]. */
    fun forActivity(sport: SportType, start: Instant, zone: ZoneId): String =
        forActivity(sport, start.atZone(zone).toLocalTime())
}

/** [ActivityTitles] from string resources, so titles follow the device language. */
@Singleton
class ResourceActivityTitles @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActivityTitles {
    override fun forActivity(sport: SportType, time: LocalTime): String {
        val resources = context.resources
        val sportName = resources.getString(sportNameRes(sport))
        return resources.getString(partOfDayRes(PartOfDay.of(time)), sportName)
    }

    internal companion object {
        @StringRes
        fun partOfDayRes(part: PartOfDay): Int = when (part) {
            PartOfDay.MORNING -> R.string.auto_title_morning
            PartOfDay.AFTERNOON -> R.string.auto_title_afternoon
            PartOfDay.EVENING -> R.string.auto_title_evening
            PartOfDay.NIGHT -> R.string.auto_title_night
        }

        /** The name resource of [sport]; exhaustive, so a new sport cannot ship without a name. */
        @StringRes
        fun sportNameRes(sport: SportType): Int = when (sport) {
            SportType.RUN -> R.string.sport_run
            SportType.TRAIL_RUN -> R.string.sport_trail_run
            SportType.VIRTUAL_RUN -> R.string.sport_virtual_run
            SportType.WALK -> R.string.sport_walk
            SportType.HIKE -> R.string.sport_hike
            SportType.SNOWSHOE -> R.string.sport_snowshoe
            SportType.RIDE -> R.string.sport_ride
            SportType.MOUNTAIN_BIKE_RIDE -> R.string.sport_mountain_bike_ride
            SportType.GRAVEL_RIDE -> R.string.sport_gravel_ride
            SportType.E_BIKE_RIDE -> R.string.sport_e_bike_ride
            SportType.E_MOUNTAIN_BIKE_RIDE -> R.string.sport_e_mountain_bike_ride
            SportType.VIRTUAL_RIDE -> R.string.sport_virtual_ride
            SportType.VELOMOBILE -> R.string.sport_velomobile
            SportType.HANDCYCLE -> R.string.sport_handcycle
            SportType.SWIM -> R.string.sport_swim
            SportType.ROWING -> R.string.sport_rowing
            SportType.VIRTUAL_ROW -> R.string.sport_virtual_row
            SportType.CANOEING -> R.string.sport_canoeing
            SportType.KAYAKING -> R.string.sport_kayaking
            SportType.STAND_UP_PADDLING -> R.string.sport_stand_up_paddling
            SportType.SURFING -> R.string.sport_surfing
            SportType.KITESURF -> R.string.sport_kitesurf
            SportType.WINDSURF -> R.string.sport_windsurf
            SportType.SAIL -> R.string.sport_sail
            SportType.ALPINE_SKI -> R.string.sport_alpine_ski
            SportType.BACKCOUNTRY_SKI -> R.string.sport_backcountry_ski
            SportType.NORDIC_SKI -> R.string.sport_nordic_ski
            SportType.SNOWBOARD -> R.string.sport_snowboard
            SportType.ICE_SKATE -> R.string.sport_ice_skate
            SportType.INLINE_SKATE -> R.string.sport_inline_skate
            SportType.ROLLER_SKI -> R.string.sport_roller_ski
            SportType.SKATEBOARD -> R.string.sport_skateboard
            SportType.WHEELCHAIR -> R.string.sport_wheelchair
            SportType.WORKOUT -> R.string.sport_workout
            SportType.WEIGHT_TRAINING -> R.string.sport_weight_training
            SportType.CROSSFIT -> R.string.sport_crossfit
            SportType.HIGH_INTENSITY_INTERVAL_TRAINING -> R.string.sport_high_intensity_interval_training
            SportType.ELLIPTICAL -> R.string.sport_elliptical
            SportType.STAIR_STEPPER -> R.string.sport_stair_stepper
            SportType.YOGA -> R.string.sport_yoga
            SportType.PILATES -> R.string.sport_pilates
            SportType.PHYSICAL_THERAPY -> R.string.sport_physical_therapy
            SportType.DANCE -> R.string.sport_dance
            SportType.BADMINTON -> R.string.sport_badminton
            SportType.PICKLEBALL -> R.string.sport_pickleball
            SportType.PADEL -> R.string.sport_padel
            SportType.RACQUETBALL -> R.string.sport_racquetball
            SportType.SQUASH -> R.string.sport_squash
            SportType.TABLE_TENNIS -> R.string.sport_table_tennis
            SportType.TENNIS -> R.string.sport_tennis
            SportType.BASKETBALL -> R.string.sport_basketball
            SportType.CRICKET -> R.string.sport_cricket
            SportType.SOCCER -> R.string.sport_soccer
            SportType.VOLLEYBALL -> R.string.sport_volleyball
            SportType.GOLF -> R.string.sport_golf
            SportType.ROCK_CLIMBING -> R.string.sport_rock_climbing
            SportType.OTHER -> R.string.sport_other
        }
    }
}
