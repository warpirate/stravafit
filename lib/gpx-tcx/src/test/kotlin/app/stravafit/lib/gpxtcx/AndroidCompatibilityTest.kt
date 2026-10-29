// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixtures in the dialects Android apps write, read by the dependency-free tokenizer. */
class AndroidCompatibilityTest {
    private fun read(path: String) = Fixtures.open(path).use { GpxReader().read(it) }

    @Test
    fun `reads prefixed namespace, CDATA, comments and character references`() {
        val doc = read("gpx/android-tracker-cdata-comments.gpx")
        assertEquals("Tracker & Co — v2", doc.creator)
        assertEquals("Run <with> \"markup\" & stuff", doc.metadata.name)
        assertEquals("Café loop <3", doc.metadata.description)
        val track = doc.tracks.single()
        assertEquals("Lunch run", track.name)
        assertEquals("running", track.type)
        val points = track.segments.single().points
        assertEquals(3, points.size)
        assertEquals(
            TrackPoint(47.3769, 8.5417, 408.5, Instant.parse("2026-06-01T12:00:00Z"), heartRate = 120, cadence = 80),
            points[0],
        )
        assertEquals(124, points[1].heartRate)
        assertEquals(47.3772, points[2].latitude)
        assertNull(points[2].time)
    }

    @Test
    fun `reads UTF-16 with byte-order mark and CRLF line endings`() {
        val doc = read("gpx/utf16-bom-crlf.gpx")
        assertEquals("Zürich exporter", doc.creator)
        assertEquals("Über den Berg", doc.tracks.single().name)
        assertEquals(2000.0, doc.allTrackPoints.single().elevation)
    }

    @Test
    fun `honours the encoding declared in the XML declaration`() {
        assertEquals("Köln Ring", read("gpx/latin1-declared.gpx").tracks.single().name)
    }

    @Test
    fun `mismatched end tags are rejected with a location`() {
        val xml = "<gpx>\n<trk>\n<name>x</nam>\n</trk></gpx>"
        val error = assertFailsWith<TrackFileFormatException> { GpxReader().read(xml.byteInputStream()) }
        assertEquals(3, error.line)
        assertTrue("does not match" in error.message!!)
    }

    @Test
    fun `entity expansion attacks are rejected`() {
        val billionLaughs = """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;">]>
            <gpx creator="&lol2;"/>
        """.trimIndent()
        assertFailsWith<TrackFileFormatException> { GpxReader().read(billionLaughs.byteInputStream()) }
    }

    @Test
    fun `round-trips through the writer`() {
        val original = read("gpx/android-tracker-cdata-comments.gpx")
        val written = GpxWriter().writeToString(original)
        val reread = GpxReader().read(written.byteInputStream())
        assertEquals(original.metadata, reread.metadata)
        assertEquals(original.allTrackPoints, reread.allTrackPoints)
        assertEquals(original.creator, reread.creator)
    }
}
