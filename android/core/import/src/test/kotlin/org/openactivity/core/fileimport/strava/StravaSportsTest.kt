// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.model.SportType

class StravaSportsTest {

    @Test
    fun `display names from the csv`() {
        val expected = mapOf(
            "Run" to SportType.RUN,
            "Trail Run" to SportType.TRAIL_RUN,
            "Virtual Run" to SportType.VIRTUAL_RUN,
            "Walk" to SportType.WALK,
            "Hike" to SportType.HIKE,
            "Ride" to SportType.RIDE,
            "Mountain Bike Ride" to SportType.MOUNTAIN_BIKE_RIDE,
            "Gravel Ride" to SportType.GRAVEL_RIDE,
            "E-Bike Ride" to SportType.E_BIKE_RIDE,
            "E-Mountain Bike Ride" to SportType.E_MOUNTAIN_BIKE_RIDE,
            "Virtual Ride" to SportType.VIRTUAL_RIDE,
            "Velomobile" to SportType.VELOMOBILE,
            "Handcycle" to SportType.HANDCYCLE,
            "Hand Cycle" to SportType.HANDCYCLE,
            "Swim" to SportType.SWIM,
            "Rowing" to SportType.ROWING,
            "Virtual Row" to SportType.VIRTUAL_ROW,
            "Canoe" to SportType.CANOEING,
            "Canoeing" to SportType.CANOEING,
            "Kayaking" to SportType.KAYAKING,
            "Stand Up Paddling" to SportType.STAND_UP_PADDLING,
            "Surfing" to SportType.SURFING,
            "Kitesurf" to SportType.KITESURF,
            "Windsurf" to SportType.WINDSURF,
            "Sail" to SportType.SAIL,
            "Alpine Ski" to SportType.ALPINE_SKI,
            "Backcountry Ski" to SportType.BACKCOUNTRY_SKI,
            "Nordic Ski" to SportType.NORDIC_SKI,
            "Snowboard" to SportType.SNOWBOARD,
            "Snowshoe" to SportType.SNOWSHOE,
            "Ice Skate" to SportType.ICE_SKATE,
            "Inline Skate" to SportType.INLINE_SKATE,
            "Roller Ski" to SportType.ROLLER_SKI,
            "Skateboard" to SportType.SKATEBOARD,
            "Wheelchair" to SportType.WHEELCHAIR,
            "Workout" to SportType.WORKOUT,
            "Weight Training" to SportType.WEIGHT_TRAINING,
            "Crossfit" to SportType.CROSSFIT,
            "HIIT" to SportType.HIGH_INTENSITY_INTERVAL_TRAINING,
            "High Intensity Interval Training" to SportType.HIGH_INTENSITY_INTERVAL_TRAINING,
            "Elliptical" to SportType.ELLIPTICAL,
            "Stair-Stepper" to SportType.STAIR_STEPPER,
            "Yoga" to SportType.YOGA,
            "Pilates" to SportType.PILATES,
            "Dance" to SportType.DANCE,
            "Rock Climb" to SportType.ROCK_CLIMBING,
            "Rock Climbing" to SportType.ROCK_CLIMBING,
            "Golf" to SportType.GOLF,
            "Soccer" to SportType.SOCCER,
            "Football (Soccer)" to SportType.SOCCER,
            "Tennis" to SportType.TENNIS,
            "Pickleball" to SportType.PICKLEBALL,
            "Padel" to SportType.PADEL,
            "Badminton" to SportType.BADMINTON,
            "Table Tennis" to SportType.TABLE_TENNIS,
            "Squash" to SportType.SQUASH,
            "Racquetball" to SportType.RACQUETBALL,
            "Basketball" to SportType.BASKETBALL,
            "Volleyball" to SportType.VOLLEYBALL,
            "Cricket" to SportType.CRICKET,
        )
        for ((name, sport) in expected) assertEquals(name, sport, StravaSports.fromStravaType(name))
    }

    @Test
    fun `api names and odd spacing`() {
        assertEquals(SportType.TRAIL_RUN, StravaSports.fromStravaType("TrailRun"))
        assertEquals(SportType.E_BIKE_RIDE, StravaSports.fromStravaType("  ebikeride "))
        assertEquals(SportType.MOUNTAIN_BIKE_RIDE, StravaSports.fromStravaType("MOUNTAIN BIKE RIDE"))
    }

    @Test
    fun `every sport we have survives a round trip through its api name`() {
        for (sport in SportType.entries) assertEquals(sport, StravaSports.fromStravaType(sport.apiName))
    }

    @Test
    fun `unknown and empty types become a workout`() {
        assertEquals(SportType.WORKOUT, StravaSports.fromStravaType("Underwater Basket Weaving"))
        assertEquals(SportType.WORKOUT, StravaSports.fromStravaType(""))
    }
}
