// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.notification.NotificationFormat
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Numbers on the widget. Built on [NotificationFormat] so the widget and the notification never
 * disagree; the widget adds the stricter "stopped" rule for pace.
 */
object WidgetFormat {
    /** Shown instead of a pace when stopped or crawling; never a nonsense number like 59:59. */
    const val NO_PACE: String = "--:--"

    /** "5:02", "42:10", "1:02:03". */
    fun clock(d: Duration): String = NotificationFormat.elapsed(d)

    /** "7.89" (or "7,89" where the decimal separator is a comma). */
    fun distance(meters: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        NotificationFormat.distance(meters, units, locale)

    /**
     * Pace for foot sports, speed for the rest. Pace slower than [MAX_PACE_SECONDS] per unit counts as stopped
     * and shows [NO_PACE]; speed shows "0.0" when stopped.
     */
    fun speedValue(speedMps: Double, sport: SportType, units: UnitSystem, locale: Locale = Locale.getDefault()): String {
        if (!NotificationFormat.showsPace(sport)) return NotificationFormat.speed(speedMps.coerceAtLeast(0.0), units, locale)
        if (speedMps <= 0.0 || !speedMps.isFinite()) return NO_PACE
        val seconds = (units.splitLengthMeters / speedMps).roundToLong()
        if (seconds > MAX_PACE_SECONDS) return NO_PACE
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    /** TalkBack text for a clock: "25 minutes 2 seconds", not "25 colon 02". */
    fun spokenClock(d: Duration, hours: (Long) -> String, minutes: (Long) -> String, seconds: (Long) -> String): String {
        val total = d.inWholeSeconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let(hours),
            m.takeIf { it > 0 || h > 0 }?.let(minutes),
            seconds(s),
        ).joinToString(" ")
    }

    /** A total like a week's moving time: "45m", "4h 12m". */
    fun duration(d: Duration): String {
        val minutes = d.inWholeMinutes.coerceAtLeast(0)
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    /**
     * "12.4 / 30" for a goal of [metric], in the metric's display unit (distance in km or mi, climbing in m or
     * ft, time as h and m); the unit itself is the caller's.
     */
    fun goalProgress(metric: GoalMetric, current: Double, target: Double, units: UnitSystem, locale: Locale = Locale.getDefault()): String =
        when (metric) {
            GoalMetric.DISTANCE -> "${distance(current, units, locale)} / ${goalNumber(target / units.splitLengthMeters, locale)}"
            GoalMetric.ELEVATION -> {
                val factor = if (units == UnitSystem.METRIC) 1.0 else FEET_PER_METER
                "${(current * factor).roundToLong()} / ${(target * factor).roundToLong()}"
            }
            GoalMetric.TIME -> "${duration(current.toLong().seconds)} / ${duration(target.toLong().seconds)}"
            GoalMetric.COUNT -> "${current.roundToLong()} / ${target.roundToLong()}"
        }

    /** A target without needless decimals: "30", "12.5". */
    private fun goalNumber(value: Double, locale: Locale): String =
        if (abs(value - value.roundToLong()) < 0.05) value.roundToLong().toString() else String.format(locale, "%.1f", value)

    private const val FEET_PER_METER = 3.28084

    /** 30:00 per km or mile: anything slower is standing around, not running or walking. */
    const val MAX_PACE_SECONDS: Long = 30L * 60
}

/** What the GPS line on the countdown and the GPS wait says. */
internal enum class GpsLineKind { READY, WEAK, SEARCHING, OFF }

/** The GPS line: its kind and the accuracy to print (whole metres, only with a fix). */
internal data class GpsLine(val kind: GpsLineKind, val accuracyMeters: Int?)

internal fun gpsLine(gps: GpsStatus?): GpsLine = when (gps?.state) {
    GpsState.GOOD -> GpsLine(GpsLineKind.READY, gps.accuracyMeters?.roundToInt())
    GpsState.WEAK -> GpsLine(GpsLineKind.WEAK, gps.accuracyMeters?.roundToInt())
    GpsState.NO_PERMISSION, GpsState.APPROXIMATE_ONLY, GpsState.DISABLED -> GpsLine(GpsLineKind.OFF, null)
    GpsState.SEARCHING, null -> GpsLine(GpsLineKind.SEARCHING, null)
}

/**
 * The app's links the widgets open (registered by the app, see its AppLinks): the recorder, a saved
 * activity and the You tab.
 */
internal object WidgetLinks {
    const val RECORD: String = "openactivity://record"

    fun record(sport: SportType): String = "$RECORD?sport=${sport.name}"

    fun activity(id: Long): String = "openactivity://activity/$id"

    const val YOU: String = "openactivity://you"

    /** A VIEW intent for [link] that stays inside this app and reuses its task. */
    fun intent(context: Context, link: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
