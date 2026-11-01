// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.model

/** What the athlete prefers to see for one sport: a pace (time per distance) or a speed. */
public enum class PaceOrSpeed {
    /** Time per kilometre or mile (per 100 m/yd for swims, per 500 m for rowing). */
    PACE,

    /** Distance per hour. */
    SPEED,
}

/** The distance swim paces are given per. */
public enum class SwimPaceUnit {
    /** Per 100 metres. */
    METERS,

    /** Per 100 yards. */
    YARDS,
}

/** Resolves how fast an activity is shown, from the sport's default and the athlete's choices. */
public object SpeedDisplays {
    /**
     * How [sport] shows pace or speed for an athlete using [units].
     *
     * - No [override]: the sport's own default (run, walk, hike: pace; ride: speed; swim: per 100; rowing:
     *   per 500 m).
     * - [PaceOrSpeed.SPEED]: always speed.
     * - [PaceOrSpeed.PACE]: the sport's own pace unit, or per km/mi for sports that default to speed.
     *
     * Swims are per 100 m with metric units and per 100 yd with imperial ones unless [swimUnit] picks one.
     */
    public fun resolve(
        sport: SportType,
        units: UnitSystem,
        override: PaceOrSpeed? = null,
        swimUnit: SwimPaceUnit? = null,
    ): SpeedDisplay {
        val base = when (override) {
            null -> sport.speedDisplay
            PaceOrSpeed.SPEED -> SpeedDisplay.SPEED
            PaceOrSpeed.PACE -> if (sport.speedDisplay == SpeedDisplay.SPEED) SpeedDisplay.PACE else sport.speedDisplay
        }
        if (base != SpeedDisplay.PACE_PER_100M) return base
        val yards = when (swimUnit) {
            SwimPaceUnit.YARDS -> true
            SwimPaceUnit.METERS -> false
            null -> units == UnitSystem.IMPERIAL
        }
        return if (yards) SpeedDisplay.PACE_PER_100YD else SpeedDisplay.PACE_PER_100M
    }

    /**
     * The distance in metres one pace is given per under [display] and [units] (1000, a mile, 100 m,
     * 100 yd or 500 m), or null when [display] is a speed.
     */
    public fun paceUnitMeters(display: SpeedDisplay, units: UnitSystem): Double? = when (display) {
        SpeedDisplay.PACE -> units.splitLengthMeters
        SpeedDisplay.PACE_PER_100M -> 100.0
        SpeedDisplay.PACE_PER_100YD -> 100.0 * UnitConstants.METERS_PER_YARD
        SpeedDisplay.PACE_PER_500M -> 500.0
        SpeedDisplay.SPEED -> null
    }
}
