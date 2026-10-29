// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.audio

import android.content.res.Resources
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.recordingservice.R
import org.openactivity.model.UnitSystem
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

/** Turns cues into sentences in the device language, from string resources. */
class CuePhrases(private val resources: Resources, private val locale: Locale = Locale.getDefault()) {

    fun phrase(cue: AudioCue, units: UnitSystem): String = when (cue) {
        is AudioCue.Started -> resources.getString(R.string.cue_started, DefaultTitles.sportName(cue.sport))
        AudioCue.Paused -> resources.getString(R.string.cue_paused)
        AudioCue.Resumed -> resources.getString(R.string.cue_resumed)
        AudioCue.AutoPaused -> resources.getString(R.string.cue_auto_paused)
        AudioCue.AutoResumed -> resources.getString(R.string.cue_auto_resumed)
        AudioCue.Saved -> resources.getString(R.string.cue_saved)
        is AudioCue.Milestone -> milestone(cue, units)
    }

    private fun milestone(cue: AudioCue.Milestone, units: UnitSystem): String = buildList {
        add(resources.getString(R.string.cue_distance, distance(cue.distanceMeters, units)))
        add(resources.getString(R.string.cue_time, duration(cue.movingTime.inWholeSeconds)))
        cue.intervalPaceSecondsPerUnit?.takeIf { it.isFinite() && it > 0 }?.let { pace ->
            val unit = resources.getString(if (units == UnitSystem.METRIC) R.string.cue_unit_kilometer else R.string.cue_unit_mile)
            add(resources.getString(R.string.cue_split_pace, duration(pace.roundToLong()), unit))
        }
    }.joinToString(" ")

    /** "2 kilometres", "2.5 miles". */
    fun distance(meters: Double, units: UnitSystem): String {
        val value = meters / units.splitLengthMeters
        val rounded = (value * 100).roundToLong() / 100.0
        val whole = abs(rounded - rounded.roundToLong()) < 1e-9
        val text = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(rounded)
        // Fractions are always plural in English ("1.5 kilometres"), so use a non-one quantity for them.
        val quantity = if (whole) rounded.roundToLong().toInt() else FRACTION_QUANTITY
        val plural = if (units == UnitSystem.METRIC) R.plurals.cue_kilometers else R.plurals.cue_miles
        return resources.getQuantityString(plural, quantity, text)
    }

    /** "1 hour 5 minutes 3 seconds"; zero parts are left out. */
    fun duration(totalSeconds: Long): String {
        val d = totalSeconds.coerceAtLeast(0).seconds
        val parts = d.toComponents { hours, minutes, seconds, _ ->
            buildList {
                if (hours > 0) add(resources.getQuantityString(R.plurals.cue_hours, hours.toInt(), hours.toInt()))
                if (minutes > 0) add(resources.getQuantityString(R.plurals.cue_minutes, minutes, minutes))
                if (seconds > 0 || (hours == 0L && minutes == 0)) {
                    add(resources.getQuantityString(R.plurals.cue_seconds, seconds, seconds))
                }
            }
        }
        return parts.joinToString(" ")
    }

    private companion object {
        const val FRACTION_QUANTITY = 2
    }
}
