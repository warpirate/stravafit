// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.geojson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoJsonTest {
    private val reader = GeoJsonReader()

    private val track = Feature(
        geometry = LineString(
            listOf(
                Position.fromLatLon(52.520000049, 13.405000051, 34.25),
                Position.fromLatLon(52.5201, 13.4052, 34.6),
                Position.fromLatLon(52.521, 13.407),
            ),
        ),
        properties = linkedMapOf(
            "name" to "Morning \"Ride\"\n",
            "sport" to "ride",
            "distance_m" to 1234.5,
            "elapsed_s" to 3600,
            "moving" to true,
            "gear" to null,
            "splits" to listOf(300, 295.5),
            "meta" to mapOf("tiny" to 1.0E-7),
        ),
        id = "act-42",
    )

    @Test
    fun `writes lon-lat order with precision, fixed member order and escaped strings`() {
        val json = GeoJsonWriter().write(track)
        assertEquals(
            """{"type":"Feature","id":"act-42","geometry":{"type":"LineString","coordinates":""" +
                """[[13.405,52.52,34.3],[13.4052,52.5201,34.6],[13.407,52.521]]},"properties":""" +
                """{"name":"Morning \"Ride\"\n","sport":"ride","distance_m":1234.5,"elapsed_s":3600,""" +
                """"moving":true,"gear":null,"splits":[300,295.5],"meta":{"tiny":0.0000001}}}""",
            json,
        )
    }

    @Test
    fun `pretty output with bbox matches golden`() {
        val collection = FeatureCollection(
            listOf(
                track,
                Feature(Point(Position(-0.1246, 51.5007)), mapOf("kind" to "start"), id = 7),
                Feature(
                    MultiLineString(listOf(listOf(Position(0.0, 0.0), Position(1.0, 1.0)), listOf(Position(2.0, 2.0), Position(3.0, 3.0)))),
                    properties = null,
                ),
            ),
        )
        val options = GeoJsonWriteOptions(prettyPrint = true, includeBbox = true)
        Fixtures.assertGolden("writer-collection.geojson", GeoJsonWriter(options).write(collection))
    }

    @Test
    fun `full precision and 2-D options`() {
        val json = GeoJsonWriter(GeoJsonWriteOptions(coordinateDecimals = null, includeAltitude = false))
            .write(track.geometry!!)
        assertEquals(
            """{"type":"LineString","coordinates":[[13.405000051,52.520000049],[13.4052,52.5201],[13.407,52.521]]}""",
            json,
        )
    }

    @Test
    fun `round trip is lossless at full precision`() {
        val writer = GeoJsonWriter(GeoJsonWriteOptions(coordinateDecimals = null, altitudeDecimals = null))
        val back = reader.read(writer.write(track))
        val expected = track.copy(
            properties = track.properties!! + mapOf("elapsed_s" to 3600L, "splits" to listOf(300L, 295.5)),
        )
        assertEquals(expected, back)
    }

    @Test
    fun `reads feature fixture ignoring bbox, crs, foreign members and extra position values`() {
        val feature = Fixtures.open("geojson/activity-feature.geojson").use { reader.read(it) }
        assertIs<Feature>(feature)
        assertEquals("act-42", feature.id)
        val line = assertIs<LineString>(feature.geometry)
        assertEquals(Position(13.4052, 52.5201, 34.6), line.coordinates[1])
        assertNull(line.coordinates[2].altitude)
        val props = feature.properties!!
        assertEquals("Morning Ride é \"quoted\"", props["name"])
        assertEquals(1234.5, props["distance_m"])
        assertEquals(listOf(300L, 295.5), props["splits"])
        assertEquals(mapOf("name" to "Handcrafted", "battery" to 87L), props["device"])
        assertTrue(props.containsKey("hidden") && props["hidden"] == null)
    }

    @Test
    fun `reads every geometry type`() {
        val fc = assertIs<FeatureCollection>(Fixtures.open("geojson/mixed-collection.geojson").use { reader.read(it) })
        assertEquals(5, fc.features.size)
        assertEquals(Point(Position(-0.1246, 51.5007)), fc.features[0].geometry)
        assertIs<MultiLineString>(fc.features[1].geometry)
        assertNull(fc.features[1].properties)
        assertIs<Polygon>(fc.features[2].geometry)
        assertNull(fc.features[3].geometry)
        assertEquals(7L, fc.features[3].id)
        val gc = assertIs<GeometryCollection>(fc.features[4].geometry)
        assertEquals(listOf(MultiPoint::class, MultiPolygon::class), gc.geometries.map { it::class })
        assertEquals(1 + 4 + 5 + 0 + 6, fc.features.sumOf { it.geometry?.positions?.count() ?: 0 })
    }

    @Test
    fun `golden output reads back to the same model`() {
        val golden = Fixtures.open("golden/writer-collection.geojson").use { reader.read(it) }
        val fc = assertIs<FeatureCollection>(golden)
        assertEquals(3, fc.features.size)
        assertEquals(Position(13.405, 52.52, 34.3), (fc.features[0].geometry as LineString).coordinates[0])
    }

    @Test
    fun `rejects invalid GeoJSON with a path`() {
        val ring = assertFailsWith<GeoJsonFormatException> {
            Fixtures.open("geojson/malformed-open-ring.geojson").use { reader.read(it) }
        }
        assertTrue("root.geometry" in ring.message!!, ring.message)
        val comma = assertFailsWith<GeoJsonFormatException> {
            Fixtures.open("geojson/malformed-trailing-comma.geojson").use { reader.read(it) }
        }
        assertTrue("line 1" in comma.message!!, comma.message)
        listOf(
            """{"type":"LineString","coordinates":[[1,2]]}""",
            """{"type":"Point","coordinates":[1]}""",
            """{"type":"Point","coordinates":["a","b"]}""",
            """{"type":"Circle","coordinates":[1,2]}""",
            """{"coordinates":[1,2]}""",
            """{"type":"Feature","geometry":null,"properties":{},"id":true}""",
            """[1,2]""",
            """{"type":"Point","coordinates":[1,2]} x""",
            "[".repeat(1000),
        ).forEach { bad -> assertFailsWith<GeoJsonFormatException>(bad.take(60)) { reader.read(bad) } }
    }

    @Test
    fun `model and writer validate input`() {
        assertFailsWith<IllegalArgumentException> { LineString(listOf(Position(0.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { Position(Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { Feature(null, id = listOf(1)) }
        assertFailsWith<IllegalArgumentException> {
            GeoJsonWriter().write(Feature(null, mapOf("bad" to Double.POSITIVE_INFINITY)))
        }
        assertFailsWith<IllegalArgumentException> { GeoJsonWriter().write(Feature(null, mapOf("bad" to Any()))) }
    }
}
