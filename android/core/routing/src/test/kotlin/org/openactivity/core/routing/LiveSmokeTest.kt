// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.model.LatLng

/**
 * Calls the real FOSSGIS OSRM and Open-Meteo servers for a 5 km run in Pune. Skipped unless run with
 * `-Plive=true`:
 *
 * `./gradlew :android:core:routing:testDebugUnitTest -Plive=true --tests '*LiveSmokeTest*'`
 */
class LiveSmokeTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `five km run loops in Pune`() = runBlocking {
        assumeTrue("live routing smoke test; pass -Plive=true", System.getProperty("routing.live") == "true")
        val transport = UrlConnectionTransport(UrlConnectionTransport.userAgent("0.1.0-smoketest"))
        val suggester = RouteSuggester(
            OsrmClient(transport, RateLimiter(1_000)),
            ElevationClient(transport, RateLimiter(500)),
            SuggestionCache(folder.root),
        )
        val started = System.currentTimeMillis()
        val result = suggester.suggest(SuggestionRequest(LatLng(18.5314, 73.8446), SuggestionSport.RUN, listOf(5_000.0))).last()
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        println("LIVE: ${result.routes.size} loops in ${"%.1f".format(seconds)} s, failed=${result.failed}")
        result.routes.forEach {
            println(
                "LIVE: ${it.name} | ${"%.0f".format(it.distanceMeters)} m | gain ${it.elevationGainMeters?.let { g -> "%.0f".format(g) }} m" +
                    " loss ${it.elevationLossMeters?.let { l -> "%.0f".format(l) }} m | ${it.points.size} pts | ${it.profile.size} elevation samples" +
                    " | reuse ${"%.2f".format(RouteShape.reuseFraction(it.points))}",
            )
        }
        assertTrue(!result.failed)
        assertTrue(result.routes.isNotEmpty())
    }
}
