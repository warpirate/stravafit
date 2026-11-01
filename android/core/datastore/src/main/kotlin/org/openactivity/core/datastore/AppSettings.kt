// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import org.openactivity.model.PaceOrSpeed
import org.openactivity.model.SpeedDisplay
import org.openactivity.model.SpeedDisplays
import org.openactivity.model.SportType
import org.openactivity.model.SwimPaceUnit
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.time.DayOfWeek
import java.util.Locale

/** Appearance preference. */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/** The tab the app opens on at a cold launch (settings-privacy-units 2.5). */
enum class LaunchTab {
    HOME,
    MAPS,
    RECORD,
    YOU,
}

/**
 * Spoken feedback while recording (record detail 7.1-7.4, A10): a master switch plus one toggle per kind
 * of cue, so an athlete can keep the split readouts and silence the rest, or the other way round.
 *
 * @property enabled master switch for all cues.
 * @property distanceInterval announce every this many kilometres or miles (following the unit setting),
 *   when [distanceCues] is on.
 * @property announceAutoPause speak when auto-pause stops or restarts the timer (record detail 7.4).
 * @property announceStatus "Activity started", paused, resumed and saved.
 * @property announceSplits each completed kilometre or mile split, with its pace.
 * @property announceLaps each lap, manual or automatic.
 * @property announceRoute off route, back on route and route finished, while following a route.
 * @property distanceCues the custom distance interval cue ([distanceInterval]).
 * @property timeIntervalMinutes a time and distance readout every this many minutes of timer time; 0 is off.
 */
data class AudioCueSettings(
    val enabled: Boolean = true,
    val distanceInterval: Double = 1.0,
    val announceAutoPause: Boolean = true,
    val announceStatus: Boolean = true,
    val announceSplits: Boolean = true,
    val announceLaps: Boolean = true,
    val announceRoute: Boolean = true,
    val distanceCues: Boolean = false,
    val timeIntervalMinutes: Int = 0,
) {
    init {
        require(distanceInterval > 0.0 && distanceInterval.isFinite()) { "interval must be positive" }
        require(timeIntervalMinutes >= 0) { "time interval must not be negative" }
    }

    companion object {
        /** Distance interval choices, in kilometres or miles (record detail 7.3, plus longer ones). */
        val DISTANCE_INTERVALS: List<Double> = listOf(0.25, 0.5, 1.0, 2.0, 5.0)

        /** Time interval choices in minutes; 0 is off. */
        val TIME_INTERVALS: List<Int> = listOf(0, 5, 10, 15, 30)
    }
}

/**
 * Distance auto-lap choices (record detail 3.10). [CUSTOM] uses [AppSettings.autoLapMeters] as stored.
 */
enum class AutoLapChoice {
    OFF,
    KILOMETER,
    MILE,
    CUSTOM,
    ;

    companion object {
        const val KILOMETER_METERS: Double = 1000.0
        const val MILE_METERS: Double = 1609.344

        /** The choice a stored [meters] value corresponds to. */
        fun of(meters: Double): AutoLapChoice = when {
            meters <= 0.0 -> OFF
            kotlin.math.abs(meters - KILOMETER_METERS) < 0.5 -> KILOMETER
            kotlin.math.abs(meters - MILE_METERS) < 0.5 -> MILE
            else -> CUSTOM
        }
    }
}

/**
 * Map privacy defaults applied to new activities.
 *
 * @property hideStartMeters radius around the start hidden from other people (0 turns it off).
 * @property hideEndMeters radius around the end hidden from other people.
 */
data class PrivacySettings(
    val hideStartMeters: Double = DEFAULT_HIDE_METERS,
    val hideEndMeters: Double = DEFAULT_HIDE_METERS,
) {
    init {
        require(hideStartMeters >= 0.0 && hideEndMeters >= 0.0) { "radii must not be negative" }
    }

    companion object {
        /** Hidden from the very first upload (settings-privacy-units 7.1, improved). */
        const val DEFAULT_HIDE_METERS: Double = 200.0
    }
}

/**
 * Which on-device events also raise an Android notification. The in-app inbox always records them
 * (notifications-search N1); these switches only control the system notifications.
 *
 * @property achievements new personal records and 2nd/3rd best efforts.
 * @property goals a goal reached, and the one-off reminder at 80%.
 * @property weeklySummary last week's totals, on the first morning of the new week.
 */
data class NotificationSettings(
    val achievements: Boolean = true,
    val goals: Boolean = true,
    val weeklySummary: Boolean = true,
)

/**
 * Every user setting the data layer and the recording service need.
 *
 * @property autoPauseOverrides per-sport choices; sports without an entry use [defaultAutoPause].
 * @property defaultSport sport preselected on the Record screen (the last sport recorded).
 * @property firstDayOfWeek where weekly totals, weekly goals and the streak start a week (Monday, as Strava).
 * @property notifications which events also post an Android notification.
 * @property defaultVisibility visibility given to new activities of sports without an override.
 * @property speedOverrides per-sport pace-or-speed choices; sports without an entry use their default.
 * @property swimPaceUnit per 100 m or per 100 yd for swims; null follows [units].
 * @property visibilityOverrides per-sport default visibility; sports without one use [defaultVisibility].
 * @property launchTab the tab a cold launch opens on.
 * @property autoLapMeters distance auto-lap: a lap closes every this many metres; 0 is off.
 * @property offRouteAlerts banner, vibration and (with route cues on) a spoken alert when leaving a followed
 *   route; on by default (record detail 10.1).
 */
data class AppSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val autoPauseOverrides: Map<SportType, Boolean> = emptyMap(),
    val audioCues: AudioCueSettings = AudioCueSettings(),
    val defaultSport: SportType = SportType.RUN,
    val defaultVisibility: Visibility = Visibility.DEFAULT,
    val privacy: PrivacySettings = PrivacySettings(),
    val firstDayOfWeek: DayOfWeek = DEFAULT_FIRST_DAY_OF_WEEK,
    val notifications: NotificationSettings = NotificationSettings(),
    val speedOverrides: Map<SportType, PaceOrSpeed> = emptyMap(),
    val swimPaceUnit: SwimPaceUnit? = null,
    val visibilityOverrides: Map<SportType, Visibility> = emptyMap(),
    val launchTab: LaunchTab = LaunchTab.HOME,
    val autoLapMeters: Double = 0.0,
    val offRouteAlerts: Boolean = true,
) {
    /** Whether auto-pause is on for [sport]. */
    fun autoPauseEnabled(sport: SportType): Boolean = autoPauseOverrides[sport] ?: defaultAutoPause(sport)

    /** How [sport] shows pace or speed for this athlete, everywhere in the app. */
    fun speedDisplay(sport: SportType): SpeedDisplay =
        SpeedDisplays.resolve(sport, units, speedOverrides[sport], swimPaceUnit)

    /** Pace or speed for [sport] as currently shown, the value the per-sport setting displays. */
    fun paceOrSpeed(sport: SportType): PaceOrSpeed =
        if (speedDisplay(sport) == SpeedDisplay.SPEED) PaceOrSpeed.SPEED else PaceOrSpeed.PACE

    /** Visibility prefilled for a new activity of [sport]. */
    fun defaultVisibility(sport: SportType): Visibility = visibilityOverrides[sport] ?: defaultVisibility

    companion object {
        /** Weeks run Monday to Sunday unless the athlete picks another start. */
        val DEFAULT_FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

        /** On for rides, off for everything else (record detail 6.2). */
        fun defaultAutoPause(sport: SportType): Boolean = sport.isRideLike

        private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")

        /** First-run units: imperial only in the US, Liberia and Myanmar (settings detail 2.4). */
        fun defaultUnits(locale: Locale): UnitSystem =
            if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_REGIONS) UnitSystem.IMPERIAL else UnitSystem.METRIC
    }
}
