package routeexport

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardGridTest {
    private val box = Box(84.0, 400.0, 912.0, 800.0)

    @Test
    fun nothingToLayOut() {
        assertTrue(CardGrid.layout(0, box, 18.0).cells.isEmpty())
        assertTrue(CardGrid.layout(3, Box(0.0, 0.0, 0.0, 100.0), 18.0).cells.isEmpty())
    }

    @Test
    fun oneShapeIsOneCentredSquare() {
        val layout = CardGrid.layout(1, box, 18.0)
        val cell = layout.cells.single()
        assertEquals(cell.w, cell.h)
        assertEquals(box.h, cell.h, 1e-9) // the box is wider than tall: the height limits it
        assertEquals(box.x + box.w / 2, cell.x + cell.w / 2, 1e-9)
    }

    @Test
    fun fourInASquareBoxIsTwoByTwo() {
        val layout = CardGrid.layout(4, Box(0.0, 0.0, 600.0, 600.0), 20.0)
        assertEquals(2, layout.columns)
        assertEquals(2, layout.rows)
        assertEquals(290.0, layout.cells.first().w, 1e-9)
    }

    @Test
    fun cellsStayInsideTheBoxAndNeverOverlap() {
        for (n in 1..CardGrid.MAX_SHAPES) {
            val cells = CardGrid.layout(n, box, 18.0).cells
            assertEquals(n, cells.size)
            cells.forEach { c ->
                assertTrue(c.x >= box.x - 1e-6 && c.y >= box.y - 1e-6, "cell $c outside for n=$n")
                assertTrue(c.x + c.w <= box.x + box.w + 1e-6 && c.y + c.h <= box.y + box.h + 1e-6, "cell $c outside for n=$n")
            }
            for (i in cells.indices) for (j in i + 1 until cells.size) {
                val a = cells[i]
                val b = cells[j]
                val apart = a.x + a.w <= b.x + 1e-6 || b.x + b.w <= a.x + 1e-6 || a.y + a.h <= b.y + 1e-6 || b.y + b.h <= a.y + 1e-6
                assertTrue(apart, "cells $i and $j overlap for n=$n")
            }
        }
    }

    @Test
    fun aShortLastRowIsCentred() {
        val layout = CardGrid.layout(5, Box(0.0, 0.0, 900.0, 600.0), 0.0) // 3 + 2
        assertEquals(3, layout.columns)
        val last = layout.cells.takeLast(2)
        val centre = (last.first().x + last.last().x + last.last().w) / 2
        assertEquals(450.0, centre, 1e-9)
    }

    @Test
    fun moreThanTheCapIsCapped() {
        assertEquals(CardGrid.MAX_SHAPES, CardGrid.layout(100, box, 18.0).cells.size)
    }

    @Test
    fun cellsShrinkAsTheMonthFillsUp() {
        val sides = listOf(1, 4, 9, 20, 31).map { CardGrid.layout(it, box, 18.0).cells.first().w }
        assertEquals(sides.sortedDescending(), sides)
    }
}

class PeriodCardTest {
    private fun card(routes: Int, withoutRoute: Int = 0) = PeriodCard(
        kicker = "Month in sport",
        title = "October 2026",
        stats = listOf(CardStat("Activities", "18"), CardStat("Distance", "142.3", "km"), CardStat("Time", "14h 2m")),
        routes = SampleData.variants(routes).map { it.points },
        withoutRoute = withoutRoute,
    )

    private fun parse(svg: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))

    @Test
    fun everyStyleIsValidSvg() {
        CardStyle.entries.forEach { style -> parse(RouteArt.periodCard(card(6), style)) }
    }

    @Test
    fun stickerHasNoBackground() {
        val sticker = RouteArt.periodCard(card(3), CardStyle.STICKER)
        val light = RouteArt.periodCard(card(3), CardStyle.LIGHT)
        assertFalse(sticker.contains("<rect width=\"1080\" height=\"1350\""))
        assertTrue(light.contains("<rect width=\"1080\" height=\"1350\" fill=\"#FFFFFF\""))
    }

    @Test
    fun drawsOneShapePerRouteAndCountsTheOverflow() {
        val few = RouteArt.periodCard(card(5), CardStyle.DARK)
        assertEquals(5, Regex("<path ").findAll(few).count())
        val many = RouteArt.periodCard(card(CardGrid.MAX_SHAPES + 8), CardStyle.DARK)
        assertEquals(CardGrid.MAX_SHAPES - 1, Regex("<path ").findAll(many).count())
        assertTrue(many.contains(">+9<"))
    }

    @Test
    fun emptyPeriodSaysSoAndCountsActivitiesWithoutAMap() {
        val svg = RouteArt.periodCard(card(0, withoutRoute = 2))
        assertTrue(svg.contains("No routes in this period"))
        assertTrue(svg.contains("+2 ACTIVITIES WITHOUT A MAP"))
        parse(svg)
    }

    @Test
    fun escapesUserText() {
        val svg = RouteArt.periodCard(card(1).copy(title = "Tom & Jerry <3"))
        assertTrue(svg.contains("Tom &amp; Jerry &lt;3"))
        parse(svg)
    }
}
