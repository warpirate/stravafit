package routeexport

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineTest {
    @Test
    fun decodesGoogleReferenceExample() {
        val pts = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-9); assertEquals(-120.2, pts[0].lon, 1e-9)
        assertEquals(40.7, pts[1].lat, 1e-9); assertEquals(-120.95, pts[1].lon, 1e-9)
        assertEquals(43.252, pts[2].lat, 1e-9); assertEquals(-126.453, pts[2].lon, 1e-9)
    }

    @Test
    fun roundTrips() {
        val src = SampleData.lakeLoop().points.take(200)
        val back = Polyline.decode(Polyline.encode(src))
        assertEquals(src.size, back.size)
        src.zip(back).forEach { (a, b) ->
            assertTrue(abs(a.lat - b.lat) < 1e-5 && abs(a.lon - b.lon) < 1e-5)
        }
    }
}

class GeoTest {
    @Test
    fun oneDegreeLatitudeIsAbout111km() {
        val d = Geo.haversine(TrackPoint(0.0, 0.0), TrackPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun projectorKeepsPointsInsideBox() {
        val box = Box(10.0, 20.0, 300.0, 200.0)
        val pts = SampleData.lakeLoop().points
        val proj = Projector.fit(listOf(pts), box)
        pts.map(proj::project).forEach {
            assertTrue(it.x >= box.x - 1e-6 && it.x <= box.x + box.w + 1e-6, "x out of box: $it")
            assertTrue(it.y >= box.y - 1e-6 && it.y <= box.y + box.h + 1e-6, "y out of box: $it")
        }
    }
}

class SimplifyTest {
    @Test
    fun collinearCollapsesToEndpoints() {
        val pts = (0..50).map { XY(it.toDouble(), it * 2.0) }
        assertEquals(listOf(0, 50), Simplify.douglasPeucker(pts, 0.1).toList())
    }

    @Test
    fun keepsCorner() {
        val pts = listOf(XY(0.0, 0.0), XY(5.0, 0.0), XY(10.0, 0.0), XY(10.0, 5.0), XY(10.0, 10.0))
        assertEquals(listOf(0, 2, 4), Simplify.douglasPeucker(pts, 0.5).toList())
    }
}

class PrivacyTest {
    @Test
    fun removesEverythingNearStartAndEnd() {
        val pts = SampleData.lakeLoop().points
        val trimmed = Privacy.trim(pts, 400.0)
        assertTrue(trimmed.isNotEmpty())
        assertTrue(trimmed.size < pts.size)
        trimmed.forEach {
            assertTrue(Geo.haversine(it, pts.first()) >= 400.0)
            assertTrue(Geo.haversine(it, pts.last()) >= 400.0)
        }
    }
}

class FormatTest {
    @Test
    fun formats() {
        assertEquals("5:00" to "/km", Format.pace(3000.0, 10_000.0, Units.METRIC))
        assertEquals("1:02:03", Format.duration(3723.0))
        assertEquals("10.00" to "km", Format.distance(10_000.0, Units.METRIC))
        assertEquals("6.21" to "mi", Format.distance(10_000.0, Units.IMPERIAL))
    }

    @Test
    fun rampEndpointsMatchStops() {
        assertEquals("#2DD4BF", Ramps.AURORA.at(0.0))
        assertEquals("#FB7185", Ramps.AURORA.at(1.0))
    }
}

class TemplatesTest {
    private val loop = SampleData.lakeLoop()

    private fun assertWellFormed(svg: String) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(svg.toByteArray()))
        assertEquals("svg", doc.documentElement.tagName)
    }

    @Test
    fun allTemplatesProduceValidSvg() {
        listOf(
            RouteArt.minimal(loop),
            RouteArt.aurora(loop),
            RouteArt.aurora(loop, colorBy = ColorBy.HEART_RATE, theme = Themes.DUSK),
            RouteArt.poster(loop),
            RouteArt.poster(loop, theme = Themes.PAPER, units = Units.IMPERIAL),
            RouteArt.story(loop),
            RouteArt.elevationProfile(loop),
            RouteArt.collage(SampleData.variants(4)),
            RouteArt.aurora(loop, privacyRadiusM = 500.0),
        ).forEach(::assertWellFormed)
    }

    @Test
    fun deterministic() {
        assertEquals(RouteArt.poster(loop), RouteArt.poster(loop))
    }

    @Test
    fun escapesUserText() {
        val svg = RouteArt.poster(loop.copy(title = "Tom & Jerry <run> \"fast\""))
        assertWellFormed(svg)
        assertTrue("Tom &amp; Jerry &lt;run&gt;" in svg)
    }

    @Test
    fun handlesDegenerateInput() {
        listOf(
            Track(emptyList()),
            Track(listOf(TrackPoint(1.0, 1.0))),
            Track(listOf(TrackPoint(1.0, 1.0), TrackPoint(1.0, 1.0))),
        ).forEach {
            assertWellFormed(RouteArt.minimal(it))
            assertWellFormed(RouteArt.poster(it))
            assertWellFormed(RouteArt.story(it))
        }
        assertWellFormed(RouteArt.aurora(loop, privacyRadiusM = 1_000_000.0))
    }
}

class SummaryOverrideTest {
    @Test
    fun posterPrintsSuppliedSummaryInsteadOfTrackStats() {
        val track = SampleData.lakeLoop()
        val summary = statsOf(track.points).copy(distanceM = 42_195.0)
        val svg = RouteArt.poster(track.copy(summary = summary), privacyRadiusM = 300.0)
        assertTrue(svg.contains("42.20"), "poster shows the supplied distance")
    }
}

class PathSmoothingTest {
    private fun controlPoints(d: String): List<XY> =
        Regex("""C([-\d.]+) ([-\d.]+) ([-\d.]+) ([-\d.]+)""").findAll(d).flatMap { m ->
            val v = m.groupValues.drop(1).map { it.toDouble() }
            listOf(XY(v[0], v[1]), XY(v[2], v[3]))
        }.toList()

    @Test
    fun rightAngleTurnDoesNotOvershootTheCorner() {
        // Regression: a 90° turn after a long leg threw a control point well past the corner.
        val pts = listOf(XY(0.0, 0.0), XY(1000.0, 0.0), XY(1010.0, 0.0), XY(1010.0, 10.0), XY(1010.0, 800.0))
        val cps = controlPoints(PathData.smooth(pts))
        assertTrue(cps.isNotEmpty())
        for (c in cps) {
            assertTrue(c.x <= 1010.0 + 1e-6, "control point $c beyond the corner")
            assertTrue(c.y >= -1e-6, "control point $c above the route")
        }
    }

    @Test
    fun gentleCurvesStaySmooth() {
        val pts = (0..20).map { i -> XY(i * 10.0, kotlin.math.sin(i / 3.0) * 20) }
        val d = PathData.smooth(pts)
        assertEquals(20, Regex(" C").findAll(d).count())
    }
}
