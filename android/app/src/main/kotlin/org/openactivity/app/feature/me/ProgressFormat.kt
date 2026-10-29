// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.toStat
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Pure formatting for the Progress tab: totals, the weekly chart and best-effort labels. */
internal object ProgressFormat {

    /** Sports whose running best efforts are pooled (shared metrics: run distances). */
    val RUN_SPORTS: Set<SportType> = setOf(SportType.RUN, SportType.TRAIL_RUN, SportType.VIRTUAL_RUN)

    /** Sports whose cycling best efforts are pooled. */
    val RIDE_SPORTS: Set<SportType> = SportType.entries.filter { it.category == SportCategory.BIKE }.toSet()

    /**
     * Totals as stats. Sports without GPS show time and count only (profile-progress C4); for
     * everything else distance and elevation are included.
     */
    fun totalsStats(totals: StatTotals, formatter: UnitFormatter, includeCount: Boolean, timeOnly: Boolean): List<Stat> = buildList {
        if (includeCount) add(Stat("Activities", formatter.count(totals.count)))
        if (!timeOnly) add(formatter.distance(totals.distanceMeters).toStat("Distance"))
        add(Stat("Time", UnitFormatter.compactDuration(totals.movingTime.inWholeSeconds)))
        if (!timeOnly) add(formatter.elevation(totals.elevationGainMeters).toStat("Elev gain"))
    }

    /** Twelve bars of distance, or of moving time when [timeOnly]. */
    fun weeklyChart(weeks: List<WeekTotals>, formatter: UnitFormatter, timeOnly: Boolean): WeeklyChartUi {
        val values = weeks.map { if (timeOnly) it.totals.movingTime.inWholeSeconds.toDouble() else it.totals.distanceMeters }
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 0.0
        fun label(value: Double): Pair<String, String> = if (timeOnly) {
            UnitFormatter.compactDuration(value.roundToLong()).let { it to it }
        } else {
            formatter.distance(value).let { "${it.value} ${it.unit}" to "${it.value} ${it.spokenUnit}" }
        }
        val (maxShown, maxSpoken) = label(max)
        val (_, currentSpoken) = label(values.lastOrNull() ?: 0.0)
        return WeeklyChartUi(
            fractions = values.map { if (max > 0) (it / max).toFloat() else 0f },
            maxLabel = maxShown,
            summary = "Last ${values.size} weeks. This week $currentSpoken, busiest week $maxSpoken.",
            metric = if (timeOnly) "Time" else "Distance",
        )
    }

    /** "5K", "Half marathon", "1 mile"... for a best-effort target in metres. */
    fun effortLabel(meters: Double, formatter: UnitFormatter): String {
        NAMED_EFFORTS.firstOrNull { abs(it.first - meters) < 0.5 }?.let { return it.second }
        val measure = formatter.distance(meters)
        return "${measure.value} ${measure.unit}"
    }

    /** Effort time as "h:mm:ss" from an hour, else "m:ss". */
    fun effortTime(seconds: Double): String {
        val total = seconds.roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    private const val MILE = 1609.344
    private val NAMED_EFFORTS: List<Pair<Double, String>> = listOf(
        400.0 to "400 m",
        MILE / 2 to "½ mile",
        1000.0 to "1K",
        MILE to "1 mile",
        2 * MILE to "2 mile",
        5000.0 to "5K",
        10_000.0 to "10K",
        15_000.0 to "15K",
        10 * MILE to "10 mile",
        20_000.0 to "20K",
        21_097.5 to "Half marathon",
        30_000.0 to "30K",
        42_195.0 to "Marathon",
        50_000.0 to "50K",
        100 * MILE to "100 miles",
    )
}
