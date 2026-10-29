// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.art

import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.privacyTrimmed
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import routeexport.ColorBy
import routeexport.RouteArt
import routeexport.Sport
import routeexport.Theme
import routeexport.Themes
import routeexport.Track
import routeexport.TrackPoint
import routeexport.Units
import routeexport.statsOf
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Why an image cannot be made for an activity with the chosen template. */
public enum class ArtUnavailableReason {
    /** The activity has no GPS route (indoor or manual). */
    NO_ROUTE,

    /** Privacy trimming would leave too little of the route; nothing may be shown. */
    ROUTE_HIDDEN_BY_PRIVACY,

    /** The elevation chart needs elevation samples. */
    NO_ELEVATION,
}

/** Outcome of [RouteArtComposer.compose]: an SVG document or the reason there is none. */
public sealed interface ArtResult {
    /** A standalone SVG document of [width] x [height] pixels. */
    public data class Svg(val svg: String, val width: Int, val height: Int) : ArtResult

    /** No image can be produced; the UI shows an explanation instead. */
    public data class Unavailable(val reason: ArtUnavailableReason) : ArtResult
}

/**
 * Turns an [ExportActivity] and an [ArtSpec] into route-art SVG using the shared `lib/route-art`
 * templates, so the Android PNG/PDF output and any other renderer draw the same geometry.
 *
 * Privacy trimming (when enabled) runs first; the printed stats stay those of the whole activity.
 */
public class RouteArtComposer @Inject constructor() {

    /** Why [spec] cannot be drawn for [activity], or `null` when it can. Cheap: nothing is rendered. */
    public fun unavailableReason(activity: ExportActivity, spec: ArtSpec): ArtUnavailableReason? =
        prepare(activity, spec).second

    /** Builds the SVG for [activity] with [spec]. Pure and thread-safe; call it off the main thread. */
    public fun compose(activity: ExportActivity, spec: ArtSpec): ArtResult {
        val (source, reason) = prepare(activity, spec)
        if (reason != null || source == null) return ArtResult.Unavailable(reason ?: ArtUnavailableReason.NO_ROUTE)
        val track = toTrack(source, activity)
        val theme = spec.theme.toLibTheme()
        val units = if (spec.units == ArtUnits.METRIC) Units.METRIC else Units.IMPERIAL
        val colorBy = spec.colorBy.toLibColorBy()
        val (w, h) = spec.aspect.width to spec.aspect.height
        val svg = when (spec.template) {
            ArtTemplate.MINIMAL_STICKER -> {
                val side = minOf(w, h)
                val ink = if (theme.dark) "#FFFFFF" else theme.foreground
                val inner = RouteArt.minimal(track, size = side, color = if (spec.transparent) ink else theme.route)
                SvgFrame.center(inner, side, side, w, h, background = theme.background.takeUnless { spec.transparent })
            }
            ArtTemplate.AURORA -> RouteArt.aurora(
                track, width = w, height = h, theme = theme, colorBy = colorBy, transparent = spec.transparent,
            )
            ArtTemplate.POSTER -> RouteArt.poster(track, theme = theme, units = units, colorBy = colorBy, width = w, height = h)
            ArtTemplate.STORY -> RouteArt.story(track, units = units, theme = theme, transparent = spec.transparent, colorBy = colorBy)
            ArtTemplate.ELEVATION -> RouteArt.elevationProfile(
                // The chart shows the whole profile: elevation alone reveals no location.
                toTrack(activity, activity), width = w, height = h, theme = theme, units = units,
                transparent = spec.transparent,
            )
        }
        return ArtResult.Svg(svg, w, h)
    }

    /** The (possibly trimmed) activity to draw, or the reason nothing can be drawn. */
    private fun prepare(activity: ExportActivity, spec: ArtSpec): Pair<ExportActivity?, ArtUnavailableReason?> {
        val source = if (spec.privacyTrim) {
            val trimmed = activity.privacyTrimmed()
            if (trimmed.routeHidden && spec.template.needsRoute) return null to ArtUnavailableReason.ROUTE_HIDDEN_BY_PRIVACY
            trimmed.activity
        } else {
            activity
        }
        if (spec.template.needsRoute && !source.hasRoute) return null to ArtUnavailableReason.NO_ROUTE
        if (spec.template == ArtTemplate.ELEVATION) {
            // The profile's x axis is distance along the route, so it needs positions as well.
            if (!activity.hasRoute) return null to ArtUnavailableReason.NO_ROUTE
            if (activity.positionedPoints.count { it.elevation != null } < 2) return null to ArtUnavailableReason.NO_ELEVATION
        }
        return source to null
    }

    /** Background colour (ARGB) of [theme], used behind transparent previews and for story backgrounds. */
    public fun backgroundColor(theme: ArtTheme): Int = parseColor(theme.toLibTheme().background)

    /** Accent route colour (ARGB) of [theme]. */
    public fun accentColor(theme: ArtTheme): Int = parseColor(theme.toLibTheme().route)

    private fun toTrack(source: ExportActivity, original: ExportActivity): Track {
        val start = source.points.firstNotNullOfOrNull { it.time }
        val points = source.points.filter { it.hasPosition }.map { p ->
            TrackPoint(
                lat = p.latitude!!,
                lon = p.longitude!!,
                ele = p.elevation,
                time = if (p.time != null && start != null) (p.time.toEpochMilli() - start.toEpochMilli()) / 1000.0 else null,
                hr = p.heartRate,
                power = p.power,
            )
        }
        val computed = statsOf(points)
        val stats = original.stats
        return Track(
            points = points,
            title = original.title,
            subtitle = subtitle(original),
            sport = original.sport.toLibSport(),
            summary = computed.copy(
                distanceM = stats.distanceMeters,
                movingSec = stats.movingTimeExactSeconds.takeIf { it > 0 },
                elapsedSec = stats.elapsedTimeSeconds.toDouble().takeIf { it > 0 },
                elevGainM = stats.elevationGainMeters ?: computed.elevGainM,
                avgHr = stats.averageHeartRate ?: computed.avgHr,
            ),
        )
    }

    private fun subtitle(activity: ExportActivity): String {
        val date = activity.startTime?.atZone(activity.zone)?.format(DATE_FORMAT)
        return listOfNotNull(date, activity.sport.apiName.toWords()).joinToString("  ·  ")
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

        fun String.toWords(): String = replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

        fun ArtTheme.toLibTheme(): Theme = when (this) {
            ArtTheme.MIDNIGHT -> Themes.MIDNIGHT
            ArtTheme.DUSK -> Themes.DUSK
            ArtTheme.FOREST -> Themes.FOREST
            ArtTheme.PAPER -> Themes.PAPER
        }

        fun ArtColorBy.toLibColorBy(): ColorBy = when (this) {
            ArtColorBy.PACE -> ColorBy.PACE
            ArtColorBy.HEART_RATE -> ColorBy.HEART_RATE
            ArtColorBy.ELEVATION -> ColorBy.ELEVATION
            ArtColorBy.SOLID -> ColorBy.SOLID
        }

        fun SportType.toLibSport(): Sport = when {
            this == SportType.WALK -> Sport.WALK
            this == SportType.HIKE -> Sport.HIKE
            this == SportType.SWIM -> Sport.SWIM
            category == SportCategory.BIKE -> Sport.RIDE
            category == SportCategory.FOOT -> Sport.RUN
            else -> Sport.OTHER
        }

        fun parseColor(hex: String): Int = (0xFF000000 or hex.removePrefix("#").toLong(16)).toInt()
    }
}
