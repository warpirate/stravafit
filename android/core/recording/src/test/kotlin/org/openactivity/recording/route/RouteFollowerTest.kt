// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import org.junit.jupiter.api.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.recording.route.RouteFixtures.feed
import org.openactivity.recording.route.RouteFixtures.figureEight
import org.openactivity.recording.route.RouteFixtures.legs
import org.openactivity.recording.route.RouteFixtures.loop
import org.openactivity.recording.route.RouteFixtures.origin
import org.openactivity.recording.route.RouteFixtures.outAndBack
import org.openactivity.recording.route.RouteFixtures.walk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteFollowerTest {

    private fun assertMonotonic(updates: List<RouteUpdate>) {
        updates.zipWithNext().forEach { (a, b) ->
            assertTrue(b.progress.progressMeters >= a.progress.progressMeters, "progress went back")
        }
    }

    /** Progress should track the distance actually walked along the route, never jump ahead of it. */
    private fun assertTracksWalk(updates: List<RouteUpdate>, step: Double, tolerance: Double = 40.0) {
        updates.forEachIndexed { i, u ->
            val expected = i * step
            assertEquals(expected, u.progress.progressMeters, tolerance, "progress at fix $i")
        }
    }

    @Test
    fun `route path measures length and projects points`() {
        val path = RoutePath(loop(500.0))
        assertEquals(2000.0, path.lengthMeters, 1.0)
        val beside = Spherical.destination(Spherical.destination(origin, 90.0, 250.0), 180.0, 20.0)
        val p = path.nearest(beside)
        assertEquals(0, p.segment)
        assertEquals(250.0, p.alongMeters, 0.5)
        assertEquals(20.0, p.distanceMeters, 0.5)
        assertEquals(0.0, Spherical.distance(path.pointAt(250.0), Spherical.destination(origin, 90.0, 250.0)), 0.5)
    }

    @Test
    fun `duplicate points are dropped and a single point is refused`() {
        val path = RoutePath(listOf(origin, origin, Spherical.destination(origin, 90.0, 100.0)))
        assertEquals(1, path.segmentCount)
        assertTrue(runCatching { RoutePath(listOf(origin, origin)) }.isFailure)
    }

    @Test
    fun `a loop starts at the start, not the finish, and completes once`() {
        val follower = RouteFollower(RoutePath(loop(500.0)))
        val updates = follower.feed(walk(loop(500.0), step = 5.0))
        assertEquals(0.0, updates.first().progress.progressMeters, 10.0)
        assertMonotonic(updates)
        assertTracksWalk(updates, step = 5.0)
        val last = updates.last().progress
        assertTrue(last.completed)
        assertEquals(0.0, last.remainingMeters, 30.0)
        assertEquals(1, updates.flatMap { it.events }.count { it == RouteEvent.Completed })
        assertTrue(updates.none { it.progress.offRoute })
    }

    @Test
    fun `out-and-back follows the return leg instead of jumping back to the way out`() {
        val follower = RouteFollower(RoutePath(outAndBack(1000.0)))
        val updates = follower.feed(walk(outAndBack(1000.0), step = 5.0))
        assertMonotonic(updates)
        assertTracksWalk(updates, step = 5.0)
        // Halfway back: the same place as 500 m out, but 1500 m along the route.
        val halfwayBack = updates[300].progress
        assertEquals(1500.0, halfwayBack.progressMeters, 40.0)
        assertEquals(500.0, halfwayBack.remainingMeters, 40.0)
    }

    @Test
    fun `a figure-eight passes its crossing twice without skipping a lobe`() {
        val route = figureEight(400.0)
        val follower = RouteFollower(RoutePath(route))
        val updates = follower.feed(walk(route, step = 5.0, noise = 6.0))
        assertMonotonic(updates)
        assertTracksWalk(updates, step = 5.0)
        // Back at the crossing after the first lobe: 1600 m along, not 0 and not 3200.
        val atCrossing = updates[320].progress
        assertEquals(1600.0, atCrossing.progressMeters, 40.0)
        assertTrue(updates.last().progress.completed)
    }

    @Test
    fun `off route needs more than 40 m for 10 s and back on route needs under 25 m`() {
        val route = legs(origin, 90.0 to 2000.0)
        val follower = RouteFollower(RoutePath(route))
        val onRoute = (0..60).map { Spherical.destination(origin, 90.0, it * 3.0) }
        follower.feed(onRoute)
        val here = onRoute.last()
        fun away(meters: Double) = Spherical.destination(here, 0.0, meters)

        // A 6 s excursion to 60 m: no alert.
        val brief = follower.feed(List(6) { away(60.0) }, startSecond = 100)
        assertTrue(brief.none { it.progress.offRoute })
        follower.feed(listOf(here), startSecond = 106)

        // Sustained: the alert comes once, after 10 s.
        val sustained = follower.feed(List(15) { away(60.0) }, startSecond = 200)
        val firstOff = sustained.indexOfFirst { it.progress.offRoute }
        assertEquals(10, firstOff)
        val offEvents = sustained.flatMap { it.events }.filterIsInstance<RouteEvent.OffRoute>()
        assertEquals(1, offEvents.size)
        assertEquals(60.0, offEvents.single().distanceMeters, 1.0)

        // Coming closer but still 30 m away keeps the alert (hysteresis).
        val closer = follower.feed(List(5) { away(30.0) }, startSecond = 215)
        assertTrue(closer.all { it.progress.offRoute })
        assertTrue(closer.flatMap { it.events }.isEmpty())

        // Under 25 m: back on route, once.
        val back = follower.feed(listOf(away(10.0), away(5.0)), startSecond = 220)
        assertFalse(back.last().progress.offRoute)
        assertEquals(listOf<RouteEvent>(RouteEvent.BackOnRoute), back.flatMap { it.events })
    }

    @Test
    fun `drift between the thresholds never raises an alert`() {
        val follower = RouteFollower(RoutePath(legs(origin, 90.0 to 2000.0)))
        val drift = (0 until 120).map { i ->
            val on = Spherical.destination(origin, 90.0, i * 3.0)
            // Alternates 35 m and 45 m off: never more than 40 m for 10 s in a row.
            Spherical.destination(on, 0.0, if (i % 4 == 0) 35.0 else 45.0)
        }
        val updates = follower.feed(drift)
        assertTrue(updates.none { it.progress.offRoute })
    }

    @Test
    fun `poor fixes neither move progress nor raise alerts`() {
        val follower = RouteFollower(RoutePath(legs(origin, 90.0 to 1000.0)))
        follower.feed((0..20).map { Spherical.destination(origin, 90.0, it * 3.0) })
        val before = follower.progress
        val far = Spherical.destination(origin, 0.0, 300.0)
        val updates = follower.feed(List(30) { far }, startSecond = 50, accuracy = 80.0)
        assertTrue(updates.all { it.events.isEmpty() && !it.progress.offRoute })
        assertEquals(before, follower.progress)
    }

    @Test
    fun `a shortcut rejoins further along and a short backtrack keeps progress`() {
        // An L: 600 m east then 600 m north. Cut the corner diagonally and rejoin 300 m up the second leg.
        val route = legs(origin, 90.0 to 600.0, 0.0 to 600.0)
        val follower = RouteFollower(RoutePath(route))
        follower.feed(walk(legs(origin, 90.0 to 300.0), step = 5.0, noise = 0.0))
        assertEquals(300.0, follower.progress.progressMeters, 10.0)
        val corner = Spherical.destination(origin, 90.0, 600.0)
        val rejoin = Spherical.destination(corner, 0.0, 300.0)
        val cut = walk(listOf(Spherical.destination(origin, 90.0, 300.0), rejoin), step = 5.0, noise = 0.0)
        follower.feed(cut, startSecond = 100)
        assertEquals(900.0, follower.progress.progressMeters, 15.0)

        // Walk back 100 m along the second leg: progress stays at 900.
        val backtrack = walk(listOf(rejoin, Spherical.destination(rejoin, 180.0, 100.0)), step = 5.0, noise = 0.0)
        follower.feed(backtrack, startSecond = 300)
        assertEquals(900.0, follower.progress.progressMeters, 15.0)
    }

    @Test
    fun `before joining, progress stays at zero and the distance to the route is known`() {
        val follower = RouteFollower(RoutePath(legs(origin, 90.0 to 1000.0)))
        val update = follower.onFix(Spherical.destination(origin, 180.0, 300.0), 0L, 0.0, 5.0)
        assertFalse(update.progress.joined)
        assertEquals(0.0, update.progress.progressMeters, 0.0)
        assertEquals(300.0, update.progress.distanceToRouteMeters!!, 1.0)
        assertNull(RouteFollower(RoutePath(loop())).progress.distanceToRouteMeters)
    }

    @Test
    fun `a follower restored from its state carries on exactly like the original`() {
        val route = outAndBack(1000.0)
        val positions = walk(route, step = 5.0)
        val original = RouteFollower(RoutePath(route))
        original.feed(positions.take(250))
        val restored = RouteFollower(RoutePath(route), original.state)
        val a = original.feed(positions.drop(250), startSecond = 250)
        val b = restored.feed(positions.drop(250), startSecond = 250)
        assertEquals(a, b)
        assertEquals(original.state, restored.state)
    }

    @Test
    fun `eta uses the average moving pace and waits for enough data`() {
        val eta = RouteEta.remaining(remainingMeters = 3000.0, distanceMeters = 2000.0, movingTime = kotlin.time.Duration.parse("10m"))
        assertEquals(900L, eta!!.inWholeSeconds) // 3.33 m/s
        assertNull(RouteEta.remaining(3000.0, 150.0, kotlin.time.Duration.parse("1m")))
        assertNull(RouteEta.remaining(3000.0, 2000.0, kotlin.time.Duration.parse("30s")))
        assertNull(RouteEta.remaining(0.0, 2000.0, kotlin.time.Duration.parse("10m")))
    }

    @Test
    fun `route back retraces the track and cuts out a loop`() {
        // 800 m along a road, a 1.2 km lap of a park, then 100 m on: the way back skips the park lap.
        val road = legs(origin, 90.0 to 800.0)
        val park = legs(road.last(), 0.0 to 300.0, 90.0 to 300.0, 180.0 to 300.0, 270.0 to 300.0)
        val on = legs(park.last(), 90.0 to 100.0)
        val track = walk(road + park.drop(1) + on.drop(1), step = 5.0, noise = 2.0)
        val plan = RouteBack.plan(track)!!
        assertEquals(track.last(), plan.points.first())
        assertEquals(track.first(), plan.points.last())
        assertEquals(900.0, plan.distanceMeters, 60.0)
        assertEquals(900.0, plan.straightLineMeters, 10.0)
        assertNull(RouteBack.plan(listOf(LatLng(51.0, 0.0))))
    }
}
