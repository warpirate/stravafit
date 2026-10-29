// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/** Broad family of a [SportType], used for grouping, defaults and per-family algorithm tuning. */
public enum class SportCategory {
    /** Running, walking and other on-foot sports. */
    FOOT,

    /** Cycling of any kind, including e-bikes and hand cycles. */
    BIKE,

    /** Swimming, paddling, rowing and sailing. */
    WATER,

    /** Skiing, snowboarding and skating on ice. */
    SNOW,

    /** Wheeled sports other than bikes (skates, wheelchair, roller-ski). */
    WHEELS,

    /** Gym and studio sessions that normally have no GPS track. */
    FITNESS,

    /** Racket and ball sports. */
    RACKET_BALL,

    /** Anything else. */
    OTHER,
}

/**
 * How a sport prefers to show how fast the athlete went.
 *
 * Runs show pace per kilometre or mile, swims per 100 m, rowing per 500 m; everything else shows speed.
 */
public enum class SpeedDisplay {
    /** Time per kilometre or mile. */
    PACE,

    /** Time per 100 metres. */
    PACE_PER_100M,

    /** Time per 500 metres. */
    PACE_PER_500M,

    /** Distance per hour. */
    SPEED,
}

/**
 * Sport types supported by the app.
 *
 * The [apiName] values follow the widely used activity-file and API vocabulary so imports and exports map
 * one-to-one. The enum is append-only: never rename an [apiName], because that is what is persisted.
 *
 * @property apiName stable identifier used in storage, files and the network API.
 * @property category broad family of the sport.
 * @property speedDisplay preferred way to show pace or speed.
 * @property usesGps true when the sport is normally recorded with a GPS track.
 * @property isVirtual true for indoor sessions on a virtual course (trainer, treadmill or rowing app).
 */
public enum class SportType(
    public val apiName: String,
    public val category: SportCategory,
    public val speedDisplay: SpeedDisplay,
    public val usesGps: Boolean = true,
    public val isVirtual: Boolean = false,
) {
    // Foot
    RUN("Run", SportCategory.FOOT, SpeedDisplay.PACE),
    TRAIL_RUN("TrailRun", SportCategory.FOOT, SpeedDisplay.PACE),
    VIRTUAL_RUN("VirtualRun", SportCategory.FOOT, SpeedDisplay.PACE, usesGps = false, isVirtual = true),
    WALK("Walk", SportCategory.FOOT, SpeedDisplay.PACE),
    HIKE("Hike", SportCategory.FOOT, SpeedDisplay.PACE),
    SNOWSHOE("Snowshoe", SportCategory.FOOT, SpeedDisplay.PACE),

    // Bike
    RIDE("Ride", SportCategory.BIKE, SpeedDisplay.SPEED),
    MOUNTAIN_BIKE_RIDE("MountainBikeRide", SportCategory.BIKE, SpeedDisplay.SPEED),
    GRAVEL_RIDE("GravelRide", SportCategory.BIKE, SpeedDisplay.SPEED),
    E_BIKE_RIDE("EBikeRide", SportCategory.BIKE, SpeedDisplay.SPEED),
    E_MOUNTAIN_BIKE_RIDE("EMountainBikeRide", SportCategory.BIKE, SpeedDisplay.SPEED),
    VIRTUAL_RIDE("VirtualRide", SportCategory.BIKE, SpeedDisplay.SPEED, usesGps = false, isVirtual = true),
    VELOMOBILE("Velomobile", SportCategory.BIKE, SpeedDisplay.SPEED),
    HANDCYCLE("Handcycle", SportCategory.BIKE, SpeedDisplay.SPEED),

    // Water
    SWIM("Swim", SportCategory.WATER, SpeedDisplay.PACE_PER_100M),
    ROWING("Rowing", SportCategory.WATER, SpeedDisplay.PACE_PER_500M),
    VIRTUAL_ROW("VirtualRow", SportCategory.WATER, SpeedDisplay.PACE_PER_500M, usesGps = false, isVirtual = true),
    CANOEING("Canoeing", SportCategory.WATER, SpeedDisplay.SPEED),
    KAYAKING("Kayaking", SportCategory.WATER, SpeedDisplay.SPEED),
    STAND_UP_PADDLING("StandUpPaddling", SportCategory.WATER, SpeedDisplay.SPEED),
    SURFING("Surfing", SportCategory.WATER, SpeedDisplay.SPEED),
    KITESURF("Kitesurf", SportCategory.WATER, SpeedDisplay.SPEED),
    WINDSURF("Windsurf", SportCategory.WATER, SpeedDisplay.SPEED),
    SAIL("Sail", SportCategory.WATER, SpeedDisplay.SPEED),

    // Snow / ice
    ALPINE_SKI("AlpineSki", SportCategory.SNOW, SpeedDisplay.SPEED),
    BACKCOUNTRY_SKI("BackcountrySki", SportCategory.SNOW, SpeedDisplay.SPEED),
    NORDIC_SKI("NordicSki", SportCategory.SNOW, SpeedDisplay.SPEED),
    SNOWBOARD("Snowboard", SportCategory.SNOW, SpeedDisplay.SPEED),
    ICE_SKATE("IceSkate", SportCategory.SNOW, SpeedDisplay.SPEED),

    // Wheels
    INLINE_SKATE("InlineSkate", SportCategory.WHEELS, SpeedDisplay.SPEED),
    ROLLER_SKI("RollerSki", SportCategory.WHEELS, SpeedDisplay.SPEED),
    SKATEBOARD("Skateboard", SportCategory.WHEELS, SpeedDisplay.SPEED),
    WHEELCHAIR("Wheelchair", SportCategory.WHEELS, SpeedDisplay.SPEED),

    // Gym / fitness
    WORKOUT("Workout", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    WEIGHT_TRAINING("WeightTraining", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    CROSSFIT("Crossfit", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    HIGH_INTENSITY_INTERVAL_TRAINING(
        "HighIntensityIntervalTraining", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false,
    ),
    ELLIPTICAL("Elliptical", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    STAIR_STEPPER("StairStepper", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    YOGA("Yoga", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    PILATES("Pilates", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    PHYSICAL_THERAPY("PhysicalTherapy", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),
    DANCE("Dance", SportCategory.FITNESS, SpeedDisplay.SPEED, usesGps = false),

    // Racket / ball
    BADMINTON("Badminton", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    PICKLEBALL("Pickleball", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    PADEL("Padel", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    RACQUETBALL("Racquetball", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    SQUASH("Squash", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    TABLE_TENNIS("TableTennis", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    TENNIS("Tennis", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    BASKETBALL("Basketball", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    CRICKET("Cricket", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    SOCCER("Soccer", SportCategory.RACKET_BALL, SpeedDisplay.SPEED),
    VOLLEYBALL("Volleyball", SportCategory.RACKET_BALL, SpeedDisplay.SPEED, usesGps = false),
    GOLF("Golf", SportCategory.RACKET_BALL, SpeedDisplay.SPEED),

    // Other
    ROCK_CLIMBING("RockClimbing", SportCategory.OTHER, SpeedDisplay.SPEED, usesGps = false),

    /** Forward-compatibility bucket for imported activities whose sport is unknown to this version. */
    OTHER("Other", SportCategory.OTHER, SpeedDisplay.SPEED, usesGps = false),
    ;

    /** True when the sport shows a pace (time per distance) rather than a speed. */
    public val displaysPace: Boolean get() = speedDisplay != SpeedDisplay.SPEED

    /** True for running sports, which get run best efforts and grade-adjusted pace. */
    public val isRunLike: Boolean get() = this == RUN || this == TRAIL_RUN || this == VIRTUAL_RUN

    /** True for cycling sports. */
    public val isRideLike: Boolean get() = category == SportCategory.BIKE

    public companion object {
        private val byApiName: Map<String, SportType> = entries.associateBy { it.apiName.lowercase() }

        /** Looks up a sport by its [apiName], ignoring case and surrounding blanks; null for unknown names. */
        public fun fromApiName(name: String): SportType? = byApiName[name.trim().lowercase()]
    }
}
