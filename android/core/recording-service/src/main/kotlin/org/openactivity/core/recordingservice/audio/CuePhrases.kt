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
        is AudioCue.Split -> buildList {
            add(resources.getString(R.string.cue_split, cue.number))
            add(resources.getString(R.string.cue_time, duration(cue.movingTime.inWholeSeconds)))
            pace(cue.paceSecondsPerUnit, cue.speed, units, R.string.cue_split_pace, R.string.cue_split_speed)?.let(::add)
        }.joinToString(" ")
        is AudioCue.Lap -> buildList {
            add(resources.getString(if (cue.automatic) R.string.cue_auto_lap else R.string.cue_lap, cue.number))
            add(resources.getString(R.string.cue_lap_time, duration(cue.lapTime.inWholeSeconds)))
            pace(cue.paceSecondsPerUnit, cue.speed, units, R.string.cue_lap_pace, R.string.cue_lap_speed)?.let(::add)
        }.joinToString(" ")
        is AudioCue.TimeReadout -> buildList {
            add(resources.getString(R.string.cue_time, duration(cue.timerTime.inWholeSeconds)))
            add(resources.getString(R.string.cue_distance, distance(cue.distanceMeters, units)))
            pace(cue.averagePaceSecondsPerUnit, cue.speed, units, R.string.cue_average_pace, R.string.cue_average_speed)?.let(::add)
        }.joinToString(" ")
        is AudioCue.OffRoute -> resources.getString(R.string.cue_off_route, shortDistance(cue.distanceMeters, units))
        AudioCue.BackOnRoute -> resources.getString(R.string.cue_back_on_route)
        AudioCue.RouteCompleted -> resources.getString(R.string.cue_route_completed)
        is AudioCue.SegmentAhead -> resources.getString(R.string.cue_segment_ahead, cue.name, shortDistance(cue.distanceMeters, units))
        is AudioCue.SegmentStarted -> buildList {
            add(resources.getString(R.string.cue_segment_started, cue.name))
            cue.prSeconds?.let { add(resources.getString(R.string.cue_segment_pr_to_beat, duration(it))) }
        }.joinToString(" ")
        is AudioCue.SegmentFinished -> buildList {
            add(resources.getString(R.string.cue_segment_finished, cue.name, duration(cue.elapsedSeconds)))
            val delta = cue.deltaSeconds
            when {
                cue.newPr -> add(resources.getString(R.string.cue_segment_new_pr, duration(abs(delta ?: 0L))))
                delta != null && delta > 0 -> add(resources.getString(R.string.cue_segment_behind_pr, duration(delta)))
                delta != null -> add(resources.getString(R.string.cue_segment_matched_pr))
            }
        }.joinToString(" ")
    }

    private fun milestone(cue: AudioCue.Milestone, units: UnitSystem): String = buildList {
        add(resources.getString(R.string.cue_distance, distance(cue.distanceMeters, units)))
        add(resources.getString(R.string.cue_time, duration(cue.movingTime.inWholeSeconds)))
        pace(cue.intervalPaceSecondsPerUnit, cue.speed, units, R.string.cue_split_pace, R.string.cue_split_speed)?.let(::add)
    }.joinToString(" ")

    /** "Split pace 5 minutes 2 seconds per kilometre." or, for rides, "Split speed 24.5 kilometres per hour." */
    private fun pace(secondsPerUnit: Double?, speed: Boolean, units: UnitSystem, paceRes: Int, speedRes: Int): String? {
        val pace = secondsPerUnit?.takeIf { it.isFinite() && it > 0 } ?: return null
        return if (speed) {
            val perHour = 3600.0 / pace
            val text = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(perHour)
            resources.getString(speedRes, resources.getString(if (units == UnitSystem.METRIC) R.string.cue_kmh else R.string.cue_mph, text))
        } else {
            val unit = resources.getString(if (units == UnitSystem.METRIC) R.string.cue_unit_kilometer else R.string.cue_unit_mile)
            resources.getString(paceRes, duration(pace.roundToLong()), unit)
        }
    }

    /** "120 metres" or "400 feet", rounded to 10; a kilometre or mile and up uses [distance]. */
    fun shortDistance(meters: Double, units: UnitSystem): String {
        if (meters >= units.splitLengthMeters) return distance(meters, units)
        return if (units == UnitSystem.METRIC) {
            val m = ((meters / 10).roundToLong() * 10).toInt().coerceAtLeast(10)
            resources.getQuantityString(R.plurals.cue_meters, m, m)
        } else {
            val ft = ((meters / 0.3048 / 10).roundToLong() * 10).toInt().coerceAtLeast(10)
            resources.getQuantityString(R.plurals.cue_feet, ft, ft)
        }
    }

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
