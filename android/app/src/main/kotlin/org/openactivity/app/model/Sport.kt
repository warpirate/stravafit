// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.model

/** Which three stats an activity card shows for a sport (core-screens 1.2 table). */
enum class StatTriplet {
    /** Distance, pace, time: runs, walks, hikes. */
    DistancePaceTime,

    /** Distance, elevation gain, time: rides. */
    DistanceElevationTime,

    /** Distance, pace per 100 m, time: swims. */
    SwimDistancePaceTime,

    /** Time, average heart rate, calories: workouts without GPS. */
    TimeHeartRateCalories,
}

/** Broad grouping used by the sport picker. */
enum class SportGroup(val label: String) {
    Foot("Foot sports"),
    Cycle("Cycle sports"),
    Water("Water sports"),
    Fitness("Fitness"),
}

/**
 * Sport types supported by the app shell. Mirrors a subset of the shared `SportType` enum that
 * `:shared:model` will own; the full list (50+ types) arrives with that module.
 *
 * @property usesGps false for indoor sports: no map, and GPS stays off while recording.
 */
enum class Sport(
    val label: String,
    val group: SportGroup,
    val triplet: StatTriplet,
    val usesGps: Boolean = true,
) {
    Run("Run", SportGroup.Foot, StatTriplet.DistancePaceTime),
    TrailRun("Trail run", SportGroup.Foot, StatTriplet.DistancePaceTime),
    Walk("Walk", SportGroup.Foot, StatTriplet.DistancePaceTime),
    Hike("Hike", SportGroup.Foot, StatTriplet.DistancePaceTime),
    Ride("Ride", SportGroup.Cycle, StatTriplet.DistanceElevationTime),
    MountainBikeRide("Mountain bike ride", SportGroup.Cycle, StatTriplet.DistanceElevationTime),
    GravelRide("Gravel ride", SportGroup.Cycle, StatTriplet.DistanceElevationTime),
    EBikeRide("E-bike ride", SportGroup.Cycle, StatTriplet.DistanceElevationTime),
    Swim("Swim", SportGroup.Water, StatTriplet.SwimDistancePaceTime),
    Rowing("Rowing", SportGroup.Water, StatTriplet.DistancePaceTime),
    WeightTraining("Weight training", SportGroup.Fitness, StatTriplet.TimeHeartRateCalories, usesGps = false),
    Yoga("Yoga", SportGroup.Fitness, StatTriplet.TimeHeartRateCalories, usesGps = false),
    Workout("Workout", SportGroup.Fitness, StatTriplet.TimeHeartRateCalories, usesGps = false),
}
