package routeexport

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Route-art exporters. Every function returns a standalone SVG document string.
 *
 * Pipeline: privacy trim -> Web Mercator -> fit to box -> Douglas–Peucker (px tolerance)
 * -> Catmull–Rom smoothing -> chunked strokes coloured through an OKLab ramp.
 */
object RouteArt {
    const val FONT = "Inter, 'SF Pro Display', 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
    private const val MAX_COLOR_CHUNKS = 240

    data class RouteStyle(
        val strokeWidth: Double = 10.0,
        val color: String = "#FFFFFF",
        val ramp: Ramp? = null,
        val glow: Boolean = false,
        val markers: Boolean = true,
        /** Fill of the hollow start marker; null = transparent. */
        val markerFill: String? = null,
        val opacity: Double = 1.0,
        val smooth: Boolean = true,
        /** CSS mix-blend-mode, e.g. "screen" for additive collages. */
        val blend: String? = null,
    )

    internal class Prepared(val xy: List<XY>, val t: DoubleArray?)

    internal fun prepare(
        points: List<TrackPoint>,
        projector: Projector,
        colorBy: ColorBy,
        tolerancePx: Double = 0.6,
    ): Prepared {
        val all = points.map(projector::project)
        if (all.size < 3) return Prepared(all, Metrics.normalized(points, colorBy))
        val values = Metrics.normalized(points, colorBy)
        val keep = Simplify.douglasPeucker(all, tolerancePx)
        return Prepared(keep.map { all[it] }, values?.let { v -> DoubleArray(keep.size) { v[keep[it]] } })
    }

    internal fun drawRoute(svg: Svg, p: Prepared, style: RouteStyle) {
        val pts = p.xy
        if (pts.size < 2) return
        val sw = style.strokeWidth
        val colored = p.t != null && style.ramp != null
        val chunks = chunks(pts.size, colored)

        fun colorOf(a: Int, b: Int): String =
            if (colored) style.ramp!!.at(average(p.t!!, a, b)) else style.color

        fun strokes(width: Double) {
            for ((a, b) in chunks) {
                val d = if (style.smooth) PathData.smooth(pts, a, b) else PathData.polyline(pts, a, b)
                svg.el("path", "d" to d, "stroke" to colorOf(a, b), "stroke-width" to width)
            }
        }

        svg.group(
            "fill" to "none",
            "stroke-linecap" to "round",
            "stroke-linejoin" to "round",
            "opacity" to if (style.opacity < 1.0) style.opacity else null,
            "style" to style.blend?.let { "mix-blend-mode:$it" },
        ) {
            if (style.glow) {
                val id = svg.newId("glow")
                svg.def("<filter id=\"$id\" x=\"-25%\" y=\"-25%\" width=\"150%\" height=\"150%\"><feGaussianBlur stdDeviation=\"${(sw * 1.5).f()}\"/></filter>")
                svg.group("filter" to "url(#$id)", "opacity" to 0.75) { strokes(sw * 2.2) }
            }
            strokes(sw)
        }

        if (style.markers) {
            val s = pts.first()
            val e = pts.last()
            val sc = if (colored) style.ramp!!.at(p.t!!.first()) else style.color
            val ec = if (colored) style.ramp!!.at(p.t!!.last()) else style.color
            svg.el("circle", "cx" to e.x, "cy" to e.y, "r" to sw * 2.2, "fill" to "none", "stroke" to ec, "stroke-width" to sw * 0.3, "opacity" to 0.45)
            svg.el("circle", "cx" to e.x, "cy" to e.y, "r" to sw * 1.1, "fill" to ec)
            svg.el("circle", "cx" to s.x, "cy" to s.y, "r" to sw * 1.2, "fill" to (style.markerFill ?: "none"), "stroke" to sc, "stroke-width" to sw * 0.55)
        }
    }

    private fun chunks(n: Int, colored: Boolean): List<Pair<Int, Int>> {
        if (!colored) return listOf(0 to n - 1)
        val k = max(1, ceil((n - 1).toDouble() / MAX_COLOR_CHUNKS).toInt())
        val out = ArrayList<Pair<Int, Int>>()
        var a = 0
        while (a < n - 1) {
            val b = min(a + k, n - 1)
            out += a to b
            a = b
        }
        return out
    }

    private fun average(v: DoubleArray, a: Int, b: Int): Double {
        var s = 0.0
        for (i in a..b) s += v[i]
        return s / (b - a + 1)
    }

    // ---------------------------------------------------------------- building blocks

    private fun background(svg: Svg, theme: Theme) {
        val id = svg.newId("vignette")
        svg.def("<radialGradient id=\"$id\" cx=\"50%\" cy=\"42%\" r=\"78%\"><stop offset=\"0\" stop-color=\"${theme.surface}\"/><stop offset=\"1\" stop-color=\"${theme.background}\"/></radialGradient>")
        svg.el("rect", "width" to svg.width, "height" to svg.height, "fill" to "url(#$id)")
    }

    private fun dotGrid(svg: Svg, box: Box, color: String, opacity: Double, spacing: Double = 28.0) {
        val id = svg.newId("dots")
        svg.def("<pattern id=\"$id\" x=\"${box.x.f()}\" y=\"${box.y.f()}\" width=\"${spacing.f()}\" height=\"${spacing.f()}\" patternUnits=\"userSpaceOnUse\"><circle cx=\"${(spacing / 2).f()}\" cy=\"${(spacing / 2).f()}\" r=\"1.3\" fill=\"$color\"/></pattern>")
        svg.el("rect", "x" to box.x, "y" to box.y, "width" to box.w, "height" to box.h, "fill" to "url(#$id)", "opacity" to opacity)
    }

    private fun shadowFilter(svg: Svg): String {
        val id = svg.newId("shadow")
        svg.def("<filter id=\"$id\" x=\"-20%\" y=\"-20%\" width=\"140%\" height=\"140%\"><feDropShadow dx=\"0\" dy=\"2\" stdDeviation=\"6\" flood-color=\"#000000\" flood-opacity=\"0.45\"/></filter>")
        return "url(#$id)"
    }

    private fun Svg.label(
        x: Double, y: Double, text: String, size: Double, fill: String,
        weight: Int = 400, anchor: String = "start", tracking: Double = 0.0,
        opacity: Double? = null, filter: String? = null,
    ) = el(
        "text",
        "x" to x, "y" to y,
        "font-family" to FONT, "font-size" to size, "font-weight" to weight,
        "fill" to fill, "text-anchor" to anchor,
        "letter-spacing" to if (tracking != 0.0) tracking else null,
        "opacity" to opacity, "filter" to filter,
        "style" to "font-variant-numeric: tabular-nums",
        content = text,
    )

    private fun Svg.valueWithUnit(
        x: Double, y: Double, value: String, unit: String, size: Double,
        fill: String, unitFill: String, filter: String? = null,
    ) {
        val unitPart = if (unit.isEmpty()) "" else
            "<tspan font-size=\"${(size * 0.4).f()}\" font-weight=\"500\" fill=\"$unitFill\" dx=\"${(size * 0.1).f()}\">${Svg.esc(unit)}</tspan>"
        val filterAttr = if (filter == null) "" else " filter=\"$filter\""
        raw("<text x=\"${x.f()}\" y=\"${y.f()}\" font-family=\"${Svg.esc(FONT)}\" font-size=\"${size.f()}\" font-weight=\"700\" fill=\"$fill\" letter-spacing=\"-1\" style=\"font-variant-numeric: tabular-nums\"$filterAttr>${Svg.esc(value)}$unitPart</text>")
    }

    internal data class StatCell(val label: String, val value: String, val unit: String)

    internal fun statCells(track: Track, units: Units, points: List<TrackPoint> = track.points): List<StatCell> {
        val s = track.summary ?: statsOf(points)
        val cells = ArrayList<StatCell>()
        val (dv, du) = Format.distance(s.distanceM, units)
        cells += StatCell("Distance", dv, du)
        val moving = s.movingSec
        if (moving != null && moving > 0) {
            cells += StatCell("Time", Format.duration(moving), "")
            if (s.distanceM > 0) {
                val (v, u) = if (track.sport.showsPace) Format.pace(moving, s.distanceM, units)
                else Format.speed(moving, s.distanceM, units)
                cells += StatCell(if (track.sport.showsPace) "Pace" else "Speed", v, u)
            }
        }
        s.elevGainM?.let {
            val (ev, eu) = Format.elevation(it, units)
            cells += StatCell("Elev Gain", ev, eu)
        }
        return cells
    }

    private fun elevationArea(svg: Svg, points: List<TrackPoint>, box: Box, theme: Theme, lineColor: String) {
        val withEle = points.filter { it.ele != null }
        if (withEle.size < 2) return
        val cum = Metrics.cumulative(withEle)
        val total = max(cum.last(), 1.0)
        val eles = withEle.map { it.ele!! }
        val minE = eles.min()
        val maxE = eles.max()
        val span = max(maxE - minE, 15.0) * 1.25
        val lo = (minE + maxE) / 2 - span / 2
        val all = withEle.indices.map { i ->
            XY(box.x + cum[i] / total * box.w, box.y + box.h - (eles[i] - lo) / span * box.h)
        }
        val pts = Simplify.douglasPeucker(all, 0.5).map { all[it] }
        val line = PathData.smooth(pts)
        val bottom = box.y + box.h
        val area = "$line L${pts.last().x.f()} ${bottom.f()} L${pts.first().x.f()} ${bottom.f()} Z"

        val id = svg.newId("elev")
        val top = theme.ramp.at(0.7)
        svg.def("<linearGradient id=\"$id\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\"><stop offset=\"0\" stop-color=\"$top\" stop-opacity=\"0.55\"/><stop offset=\"1\" stop-color=\"$top\" stop-opacity=\"0\"/></linearGradient>")
        svg.el("path", "d" to area, "fill" to "url(#$id)")
        svg.el("path", "d" to line, "fill" to "none", "stroke" to lineColor, "stroke-width" to 2.0, "stroke-linejoin" to "round", "stroke-linecap" to "round", "opacity" to 0.9)
        svg.el("line", "x1" to box.x, "y1" to bottom, "x2" to box.x + box.w, "y2" to bottom, "stroke" to theme.muted, "stroke-width" to 1.0, "opacity" to 0.35)
    }

    // ---------------------------------------------------------------- templates

    /** Route only, transparent background — sticker for photos / stories. */
    fun minimal(
        track: Track,
        size: Int = 1080,
        color: String = "#FFFFFF",
        strokeWidth: Double = 12.0,
        padding: Double = 0.12,
        privacyRadiusM: Double = 0.0,
        markers: Boolean = true,
    ): String {
        val svg = Svg(size, size)
        val pts = Privacy.trim(track.points, privacyRadiusM)
        val pad = size * padding
        val box = Box(pad, pad, size - 2 * pad, size - 2 * pad)
        val proj = Projector.fit(listOf(pts), box)
        drawRoute(svg, prepare(pts, proj, ColorBy.SOLID), RouteStyle(strokeWidth, color, markers = markers))
        return svg.build(track.title.ifBlank { "Route" })
    }

    /** Glowing gradient route on a vignette background. */
    fun aurora(
        track: Track,
        width: Int = 1080,
        height: Int = 1080,
        theme: Theme = Themes.MIDNIGHT,
        colorBy: ColorBy = ColorBy.PACE,
        transparent: Boolean = false,
        strokeWidth: Double = 9.0,
        privacyRadiusM: Double = 0.0,
    ): String {
        val svg = Svg(width, height)
        if (!transparent) background(svg, theme)
        val pts = Privacy.trim(track.points, privacyRadiusM)
        val pad = min(width, height) * 0.12
        val box = Box(pad, pad, width - 2 * pad, height - 2 * pad)
        val proj = Projector.fit(listOf(pts), box)
        drawRoute(
            svg, prepare(pts, proj, colorBy),
            RouteStyle(strokeWidth, theme.route, ramp = theme.ramp, glow = true, markerFill = if (transparent) null else theme.background),
        )
        return svg.build(track.title.ifBlank { "Route" })
    }

    /** Print-style poster: title, framed route on a dot grid, stat row, elevation profile. */
    fun poster(
        track: Track,
        theme: Theme = Themes.MIDNIGHT,
        units: Units = Units.METRIC,
        colorBy: ColorBy = ColorBy.PACE,
        width: Int = 1200,
        height: Int = 1600,
        privacyRadiusM: Double = 0.0,
        showGrid: Boolean = true,
    ): String {
        val svg = Svg(width, height)
        val w = width.toDouble()
        val h = height.toDouble()
        val m = 96.0
        background(svg, theme)
        svg.el("rect", "x" to 36.0, "y" to 36.0, "width" to w - 72, "height" to h - 72, "fill" to "none", "stroke" to theme.muted, "stroke-width" to 1.5, "opacity" to 0.3)

        if (track.subtitle.isNotBlank()) svg.label(m, 150.0, track.subtitle.uppercase(), 22.0, theme.muted, 600, tracking = 5.0)
        if (track.title.isNotBlank()) svg.label(m, 222.0, track.title, 64.0, theme.foreground, 700, tracking = -1.5)

        val pts = Privacy.trim(track.points, privacyRadiusM)
        val hasEle = pts.count { it.ele != null } >= 2
        val routeTop = 290.0
        val routeBottom = if (hasEle) 1060.0 else 1250.0
        val box = Box(m, routeTop, w - 2 * m, routeBottom - routeTop)
        if (showGrid) dotGrid(svg, box, theme.muted, 0.22)
        val inner = Box(box.x + 40, box.y + 40, box.w - 80, box.h - 80)
        drawRoute(
            svg, prepare(pts, Projector.fit(listOf(pts), inner), colorBy),
            RouteStyle(7.0, theme.route, ramp = theme.ramp, glow = theme.dark, markerFill = theme.background),
        )

        val cells = statCells(track, units, pts)
        val statsY = routeBottom + 90
        svg.el("line", "x1" to m, "y1" to statsY - 40, "x2" to w - m, "y2" to statsY - 40, "stroke" to theme.muted, "stroke-width" to 1.0, "opacity" to 0.35)
        val colW = (w - 2 * m) / max(cells.size, 1)
        cells.forEachIndexed { i, c ->
            val x = m + i * colW
            svg.label(x, statsY, c.label.uppercase(), 17.0, theme.muted, 600, tracking = 3.0)
            svg.valueWithUnit(x, statsY + 62, c.value, c.unit, 52.0, theme.foreground, theme.muted)
        }

        if (hasEle) {
            elevationArea(svg, pts, Box(m, statsY + 130, w - 2 * m, 170.0), theme, theme.foreground)
        }

        val footY = h - 72
        svg.label(m, footY, track.sport.label.uppercase(), 15.0, theme.muted, 600, tracking = 4.0, opacity = 0.8)
        val s = statsOf(pts)
        if (s.minEleM != null && s.maxEleM != null) {
            val (lo, u) = Format.elevation(s.minEleM, units)
            val (hi, _) = Format.elevation(s.maxEleM, units)
            svg.label(w - m, footY, "MIN $lo $u  ·  MAX $hi $u".uppercase(), 15.0, theme.muted, 600, anchor = "end", tracking = 3.0, opacity = 0.8)
        }
        return svg.build(track.title.ifBlank { "Route poster" })
    }

    /** 9:16 story card. Transparent by default so it can be laid over a photo. */
    fun story(
        track: Track,
        units: Units = Units.METRIC,
        theme: Theme = Themes.MIDNIGHT,
        transparent: Boolean = true,
        colorBy: ColorBy = ColorBy.PACE,
        privacyRadiusM: Double = 0.0,
    ): String {
        val svg = Svg(1080, 1920)
        if (!transparent) background(svg, theme)
        val shadow = if (transparent) shadowFilter(svg) else null
        val fg = if (transparent) "#FFFFFF" else theme.foreground
        val sub = if (transparent) "#FFFFFF" else theme.muted

        if (track.title.isNotBlank()) svg.label(90.0, 200.0, track.title, 56.0, fg, 700, tracking = -1.0, filter = shadow)
        if (track.subtitle.isNotBlank()) svg.label(90.0, 256.0, track.subtitle.uppercase(), 24.0, sub, 600, tracking = 4.0, opacity = 0.85, filter = shadow)

        val pts = Privacy.trim(track.points, privacyRadiusM)
        val box = Box(110.0, 340.0, 860.0, 900.0)
        drawRoute(
            svg, prepare(pts, Projector.fit(listOf(pts), box), colorBy),
            RouteStyle(11.0, theme.route, ramp = theme.ramp, glow = true, markerFill = if (transparent) null else theme.background),
        )

        val cells = statCells(track, units, pts)
        cells.forEachIndexed { i, c ->
            val x = if (i % 2 == 0) 90.0 else 570.0
            val y = 1400.0 + (i / 2) * 200.0
            svg.label(x, y, c.label.uppercase(), 26.0, sub, 600, tracking = 4.0, opacity = 0.85, filter = shadow)
            svg.valueWithUnit(x, y + 96, c.value, c.unit, 92.0, fg, sub, filter = shadow)
        }
        return svg.build(track.title.ifBlank { "Story" })
    }

    /** Standalone elevation profile. */
    fun elevationProfile(
        track: Track,
        width: Int = 1200,
        height: Int = 360,
        theme: Theme = Themes.MIDNIGHT,
        units: Units = Units.METRIC,
        transparent: Boolean = false,
    ): String {
        val svg = Svg(width, height)
        if (!transparent) svg.el("rect", "width" to width, "height" to height, "fill" to theme.background)
        val box = Box(48.0, 84.0, width - 96.0, height - 140.0)
        elevationArea(svg, track.points, box, theme, theme.foreground)

        val s = track.stats()
        svg.label(48.0, 50.0, "ELEVATION", 16.0, theme.muted, 600, tracking = 4.0)
        if (s.minEleM != null && s.maxEleM != null) {
            val (lo, u) = Format.elevation(s.minEleM, units)
            val (hi, _) = Format.elevation(s.maxEleM, units)
            svg.label(width - 48.0, 50.0, "MIN $lo $u  ·  MAX $hi $u".uppercase(), 16.0, theme.muted, 600, anchor = "end", tracking = 3.0)
        }
        val (dv, du) = Format.distance(s.distanceM, units)
        svg.label(48.0, height - 24.0, "0", 15.0, theme.muted, 500)
        svg.label(width - 48.0, height - 24.0, "$dv $du", 15.0, theme.muted, 500, anchor = "end")
        return svg.build(track.title.ifBlank { "Elevation" })
    }

    /** Many activities overlaid with additive blending — overlaps glow brighter (personal heatmap art). */
    fun collage(
        tracks: List<Track>,
        size: Int = 1600,
        theme: Theme = Themes.MIDNIGHT,
        units: Units = Units.METRIC,
        title: String = "",
        privacyRadiusM: Double = 0.0,
    ): String {
        val svg = Svg(size, size)
        background(svg, theme)
        val trimmed = tracks.map { Privacy.trim(it.points, privacyRadiusM) }
        val m = 120.0
        val box = Box(m, m, size - 2 * m, size - 2 * m - 140)
        val proj = Projector.fit(trimmed, box)
        val base = RouteStyle(
            strokeWidth = 3.6, color = theme.route, markers = false,
            opacity = 0.6, blend = if (theme.dark) "screen" else "multiply",
        )
        // Each activity gets its own hue from the theme ramp; overlaps blend brighter.
        trimmed.forEachIndexed { i, pts ->
            val t = if (trimmed.size > 1) i.toDouble() / (trimmed.size - 1) else 0.5
            drawRoute(svg, prepare(pts, proj, ColorBy.SOLID, 0.8), base.copy(color = theme.ramp.at(t)))
        }

        val total = trimmed.sumOf { statsOf(it).distanceM }
        val (dv, du) = Format.distance(total, units)
        svg.label(m, size - 120.0, title.ifBlank { "${tracks.size} activities" }, 44.0, theme.foreground, 700, tracking = -1.0)
        svg.label(m, size - 76.0, "${tracks.size} ACTIVITIES  ·  $dv ${du.uppercase()}", 18.0, theme.muted, 600, tracking = 4.0)
        return svg.build(title.ifBlank { "Collage" })
    }
}
