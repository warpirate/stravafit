// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtResult
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.art.RouteArtComposer
import org.openactivity.core.export.model.ExportPrivacy
import org.openactivity.core.export.model.privacyTrimmed

class RouteArtComposerTest {
    private val composer = RouteArtComposer()
    private val run = TestActivities.lakeRun()

    @Test
    fun `every template and aspect produces an svg of the requested size`() {
        for (template in ArtTemplate.entries) {
            for (aspect in template.aspects) {
                val result = composer.compose(run, ArtSpec(template = template, aspect = aspect))
                assertTrue("$template $aspect", result is ArtResult.Svg)
                result as ArtResult.Svg
                assertEquals(aspect.width, result.width)
                assertEquals(aspect.height, result.height)
                assertTrue(result.svg.trimStart().startsWith("<svg"))
                assertTrue("viewBox matches", result.svg.contains("viewBox=\"0 0 ${aspect.width} ${aspect.height}\""))
            }
        }
    }

    @Test
    fun `cards print the activity's own stats, not the trimmed track's`() {
        val svg = (composer.compose(run, ArtSpec(template = ArtTemplate.POSTER)) as ArtResult.Svg).svg
        assertTrue("distance 4.98 km on poster", svg.contains("4.98"))
        assertTrue("title is escaped", svg.contains("Lake loop &amp; café"))
    }

    @Test
    fun `activities without gps cannot draw a route`() {
        val result = composer.compose(TestActivities.indoorRide(), ArtSpec(template = ArtTemplate.AURORA))
        assertEquals(ArtResult.Unavailable(ArtUnavailableReason.NO_ROUTE), result)
    }

    @Test
    fun `privacy trimming that leaves too little hides the route`() {
        val greedy = run.copy(privacy = ExportPrivacy(hideStartMeters = 2600.0, hideEndMeters = 2600.0))
        assertEquals(
            ArtResult.Unavailable(ArtUnavailableReason.ROUTE_HIDDEN_BY_PRIVACY),
            composer.compose(greedy, ArtSpec(privacyTrim = true)),
        )
        assertTrue(composer.compose(greedy, ArtSpec(privacyTrim = false)) is ArtResult.Svg)
    }

    @Test
    fun `privacy trim strips positions at both ends but keeps sensor data`() {
        val trimmed = run.privacyTrimmed()
        assertFalse(trimmed.routeHidden)
        assertTrue(trimmed.hiddenPoints > 0)
        assertEquals(run.points.size, trimmed.activity.points.size)
        assertFalse(trimmed.activity.points.first().hasPosition)
        assertFalse(trimmed.activity.points.last().hasPosition)
        assertEquals(run.points.first().heartRate, trimmed.activity.points.first().heartRate)
    }

    @Test
    fun `switching template keeps a supported aspect and resets an unsupported one`() {
        val spec = ArtSpec(template = ArtTemplate.AURORA, aspect = ArtAspect.STORY)
        assertEquals(ArtAspect.STORY, spec.withTemplate(ArtTemplate.MINIMAL_STICKER).aspect)
        assertEquals(ArtAspect.POSTER, spec.withTemplate(ArtTemplate.POSTER).aspect)
        assertTrue(spec.withTemplate(ArtTemplate.MINIMAL_STICKER).transparent)
    }

    @Test
    fun `non-square sticker is centred in a larger frame`() {
        val svg = (composer.compose(run, ArtSpec(template = ArtTemplate.MINIMAL_STICKER, aspect = ArtAspect.STORY)) as ArtResult.Svg).svg
        assertTrue(svg.contains("<svg x=\"0\" y=\"420\" width=\"1080\" height=\"1080\""))
    }
}
