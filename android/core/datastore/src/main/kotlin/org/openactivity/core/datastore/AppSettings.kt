// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.util.Locale

/** Appearance preference. */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Spoken feedback while recording.
 *
 * @property enabled master switch for all cues.
 * @property distanceInterval announce every this many kilometres or miles (following the unit setting).
 * @property announceAutoPause speak when auto-pause stops or restarts the timer (record detail 7.4).
 */
data class AudioCueSettings(
    val enabled: Boolean = true,
    val distanceInterval: Double = 1.0,
    val announceAutoPause: Boolean = true,
) {
    init {
        require(distanceInterval > 0.0 && distanceInterval.isFinite()) { "interval must be positive" }
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
 * Every user setting the data layer and the recording service need.
 *
 * @property autoPauseOverrides per-sport choices; sports without an entry use [defaultAutoPause].
 * @property defaultSport sport preselected on the Record screen (the last sport recorded).
 * @property defaultVisibility visibility given to new activities.
 */
data class AppSettings(
    val units: UnitSystem = UnitSystem.METRIC,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val autoPauseOverrides: Map<SportType, Boolean> = emptyMap(),
    val audioCues: AudioCueSettings = AudioCueSettings(),
    val defaultSport: SportType = SportType.RUN,
    val defaultVisibility: Visibility = Visibility.DEFAULT,
    val privacy: PrivacySettings = PrivacySettings(),
) {
    /** Whether auto-pause is on for [sport]. */
    fun autoPauseEnabled(sport: SportType): Boolean = autoPauseOverrides[sport] ?: defaultAutoPause(sport)

    companion object {
        /** On for rides, off for everything else (record detail 6.2). */
        fun defaultAutoPause(sport: SportType): Boolean = sport.isRideLike

        private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")

        /** First-run units: imperial only in the US, Liberia and Myanmar (settings detail 2.4). */
        fun defaultUnits(locale: Locale): UnitSystem =
            if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_REGIONS) UnitSystem.IMPERIAL else UnitSystem.METRIC
    }
}
