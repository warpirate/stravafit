package routeexport

import kotlin.math.ceil
import kotlin.math.min

/** One number on a [PeriodCard]: "DISTANCE 124.6 km". Values come formatted from the caller (locale, units). */
data class CardStat(val label: String, val value: String, val unit: String = "")

/**
 * A shareable summary of a period (a month, a week): totals plus a grid of that period's route shapes,
 * like a year-in-sport card.
 *
 * @property kicker small overline above the title, e.g. "MONTH IN SPORT".
 * @property title the period, e.g. "October 2026".
 * @property note one optional line under the stats, e.g. "+12% distance on the week before".
 * @property routes one point list per activity with a route, oldest first. Already privacy-trimmed by the caller.
 * @property withoutRoute activities in the period that have no route (indoor, manual); counted in the footer.
 */
data class PeriodCard(
    val kicker: String,
    val title: String,
    val stats: List<CardStat>,
    val note: String = "",
    val routes: List<List<TrackPoint>> = emptyList(),
    val withoutRoute: Int = 0,
    val footer: String = "StravaFit",
)

/** Looks of a [PeriodCard]: the app's light and dark themes, and a transparent sticker for photos. */
enum class CardStyle(
    val transparent: Boolean,
    val background: String,
    val foreground: String,
    val muted: String,
    val accent: String,
    val route: String,
    val hairline: String,
) {
    LIGHT(false, "#FFFFFF", "#242428", "#6D6D78", "#CC3D00", "#FC5200", "#E5E5EA"),
    DARK(false, "#121214", "#F2F2F4", "#A3A3AD", "#FF6A2B", "#FC5200", "#2E2E34"),

    /** No background; white ink so it reads on a photo, the overline stays orange. */
    STICKER(true, "#000000", "#FFFFFF", "#FFFFFF", "#FC5200", "#FFFFFF", "#FFFFFF"),
}

/**
 * Where the route shapes of a [PeriodCard] go: a grid of square cells, as large as the box allows.
 * Pure, so the layout is unit-tested.
 */
object CardGrid {
    /** At most this many shapes are drawn; the rest are counted in the last cell ("+12"). */
    const val MAX_SHAPES: Int = 42

    /** The layout of [count] shapes in [box]: the cells (row by row) and how many columns and rows. */
    data class Layout(val cells: List<Box>, val columns: Int, val rows: Int)

    /**
     * Picks the column count that gives the biggest square cells for [count] shapes (fewer rows on a tie),
     * then centres the grid in [box]. [gap] is the space between cells. More than [MAX_SHAPES] shapes are
     * laid out as [MAX_SHAPES] cells.
     */
    fun layout(count: Int, box: Box, gap: Double): Layout {
        val n = count.coerceAtMost(MAX_SHAPES)
        if (n <= 0 || box.w <= 0 || box.h <= 0) return Layout(emptyList(), 0, 0)
        var best = 1
        var bestSide = -1.0
        for (c in 1..n) {
            val r = ceil(n.toDouble() / c).toInt()
            val side = cellSide(box, c, r, gap)
            if (side > bestSide + EPSILON) {
                best = c
                bestSide = side
            }
        }
        val columns = best
        val rows = ceil(n.toDouble() / columns).toInt()
        val side = bestSide.coerceAtLeast(0.0)
        val gridW = columns * side + (columns - 1) * gap
        val gridH = rows * side + (rows - 1) * gap
        val x0 = box.x + (box.w - gridW) / 2
        val y0 = box.y + (box.h - gridH) / 2
        val cells = (0 until n).map { i ->
            val row = i / columns
            val col = i % columns
            // A short last row is centred too, so a lone shape does not hang off to the left.
            val inRow = if (row == rows - 1) n - row * columns else columns
            val rowOffset = (columns - inRow) * (side + gap) / 2
            Box(x0 + rowOffset + col * (side + gap), y0 + row * (side + gap), side, side)
        }
        return Layout(cells, columns, rows)
    }

    private fun cellSide(box: Box, columns: Int, rows: Int, gap: Double): Double =
        min((box.w - (columns - 1) * gap) / columns, (box.h - (rows - 1) * gap) / rows)

    private const val EPSILON = 1e-9
}

/** Renders a [PeriodCard] as a standalone SVG. Default size is 4:5, the feed-friendly portrait. */
fun RouteArt.periodCard(card: PeriodCard, style: CardStyle = CardStyle.LIGHT, width: Int = 1080, height: Int = 1350): String {
    val svg = Svg(width, height)
    val w = width.toDouble()
    val h = height.toDouble()
    val m = 84.0
    if (!style.transparent) svg.el("rect", "width" to w, "height" to h, "fill" to style.background)

    fun text(x: Double, y: Double, s: String, size: Double, fill: String, weight: Int, anchor: String = "start", tracking: Double = 0.0, opacity: Double? = null) =
        svg.el(
            "text", "x" to x, "y" to y, "font-family" to RouteArt.FONT, "font-size" to size, "font-weight" to weight,
            "fill" to fill, "text-anchor" to anchor, "letter-spacing" to if (tracking != 0.0) tracking else null,
            "opacity" to opacity, "style" to "font-variant-numeric: tabular-nums", content = s,
        )

    text(m, 128.0, card.kicker.uppercase(), 24.0, style.accent, 700, tracking = 4.0)
    text(m, 204.0, card.title, 68.0, style.foreground, 700, tracking = -1.5)

    // Stats: up to four columns.
    val stats = card.stats.take(4)
    val statsY = 290.0
    if (stats.isNotEmpty()) {
        val colW = (w - 2 * m) / stats.size
        stats.forEachIndexed { i, s ->
            val x = m + i * colW
            text(x, statsY, s.label.uppercase(), 19.0, style.muted, 600, tracking = 2.5, opacity = if (style.transparent) 0.85 else null)
            val unit = if (s.unit.isEmpty()) "" else "<tspan font-size=\"24\" font-weight=\"500\" fill=\"${style.muted}\" dx=\"6\">${Svg.esc(s.unit)}</tspan>"
            svg.raw("<text x=\"${x.f()}\" y=\"${(statsY + 62).f()}\" font-family=\"${Svg.esc(RouteArt.FONT)}\" font-size=\"54\" font-weight=\"700\" fill=\"${style.foreground}\" letter-spacing=\"-1\" style=\"font-variant-numeric: tabular-nums\">${Svg.esc(s.value)}$unit</text>")
        }
    }
    var top = statsY + 104
    if (card.note.isNotBlank()) {
        text(m, top + 10, card.note, 26.0, style.foreground, 500, opacity = 0.9)
        top += 46
    }
    svg.el("line", "x1" to m, "y1" to top, "x2" to w - m, "y2" to top, "stroke" to style.hairline, "stroke-width" to 2.0, "opacity" to if (style.transparent) 0.4 else null)

    // The route grid fills the rest above the footer.
    val footerY = h - 70
    val gridBox = Box(m, top + 40, w - 2 * m, footerY - 60 - (top + 40))
    val shown = card.routes.filter { it.size >= 2 }
    val overflow = (shown.size - CardGrid.MAX_SHAPES).coerceAtLeast(0)
    val drawn = if (overflow > 0) shown.take(CardGrid.MAX_SHAPES - 1) else shown
    val cellCount = drawn.size + if (overflow > 0) 1 else 0
    val layout = CardGrid.layout(cellCount, gridBox, gap = 18.0)
    if (layout.cells.isEmpty()) {
        text(w / 2, gridBox.y + gridBox.h / 2, "No routes in this period", 28.0, style.muted, 500, anchor = "middle")
    }
    val strokeWidth = (layout.cells.firstOrNull()?.w ?: 0.0).let { (it / 34.0).coerceIn(2.2, 7.0) }
    drawn.forEachIndexed { i, pts ->
        val cell = layout.cells[i]
        val inset = cell.w * 0.1
        val inner = Box(cell.x + inset, cell.y + inset, cell.w - 2 * inset, cell.h - 2 * inset)
        val proj = Projector.fit(listOf(pts), inner)
        RouteArt.drawRoute(svg, RouteArt.prepare(pts, proj, ColorBy.SOLID, 0.8), RouteArt.RouteStyle(strokeWidth, style.route, markers = false))
    }
    if (overflow > 0) {
        val cell = layout.cells.last()
        text(cell.x + cell.w / 2, cell.y + cell.h / 2 + 12, "+${overflow + 1}", (cell.w / 3.2).coerceIn(18.0, 44.0), style.muted, 700, anchor = "middle")
    }

    text(m, footerY, card.footer.uppercase(), 20.0, style.accent, 700, tracking = 4.0)
    if (card.withoutRoute > 0) {
        val noun = if (card.withoutRoute == 1) "activity" else "activities"
        text(w - m, footerY, "+${card.withoutRoute} $noun without a map".uppercase(), 17.0, style.muted, 600, anchor = "end", tracking = 2.0)
    }
    return svg.build(card.title.ifBlank { "Period card" })
}
