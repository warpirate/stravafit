// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.ActivityMetadataUpdate
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.data.repository.LocalRouteRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: OpenActivityDatabase
    private lateinit var activities: LocalActivityRepository
    private lateinit var routes: LocalRouteRepository

    // One day after the test recordings start.
    private val clock = Clock.fixed(Instant.ofEpochMilli(TestRecordings.START_WALL_MILLIS + 86_400_000L), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activities = LocalActivityRepository(db.activityDao(), clock)
        routes = LocalRouteRepository(db.routeDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `empty database gives empty lists, not errors`() = runTest {
        assertTrue(activities.observeActivities().first().isEmpty())
        assertNull(activities.observeActivityDetail(1).first())
        assertTrue(activities.observeAllTimeTotals().first().isEmpty())
        assertTrue(activities.observePersonalBests(setOf(SportType.RUN)).first().isEmpty())
        assertEquals(List(4) { 0 }, activities.observeWeeklyTotals(4).first().map { it.totals.count })
        assertTrue(routes.observeRoutes().first().isEmpty())
    }

    @Test
    fun `a saved recording reads back with streams, laps, splits and efforts`() = runTest {
        val finished = TestRecordings.steady(seconds = 1200)
        val id = activities.saveFinished(finished, NewActivity("rec-1", visibility = Visibility.EVERYONE), UnitSystem.METRIC)

        val detail = activities.observeActivityDetail(id).first()!!
        assertEquals("rec-1", detail.activity.uuid)
        assertEquals(Visibility.EVERYONE, detail.activity.visibility)
        assertEquals(finished.distanceMeters, detail.activity.distanceMeters, 1e-9)
        assertEquals(finished.points.size, detail.points.size)
        assertEquals(finished.laps.size, detail.laps.size)
        assertEquals(finished.splits.map { it.index }, detail.splits(UnitSystem.METRIC).map { it.index })
        assertEquals(3, detail.splits(UnitSystem.IMPERIAL).size)
        assertEquals(finished.bestEfforts.map { it.targetMeters }, detail.bestEfforts.map { it.targetMeters })
        assertEquals(listOf(id), activities.observeActivities().first().map { it.id })
    }

    @Test
    fun `saving the same recording twice is idempotent`() = runTest {
        val finished = TestRecordings.steady(seconds = 120)
        val first = activities.saveFinished(finished, NewActivity("same"), UnitSystem.METRIC)
        val second = activities.saveFinished(finished, NewActivity("same"), UnitSystem.METRIC)
        assertEquals(first, second)
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `metadata edits and delete`() = runTest {
        val id = activities.saveFinished(TestRecordings.steady(seconds = 120), NewActivity("x"), UnitSystem.METRIC)
        assertTrue(activities.updateMetadata(id, ActivityMetadataUpdate(SportType.TRAIL_RUN, "  Ridge  ", "Windy", Visibility.ONLY_ME, 8)))
        val edited = activities.observeActivity(id).first()!!
        assertEquals("Ridge", edited.title)
        assertEquals(SportType.TRAIL_RUN, edited.sport)
        assertEquals(8, edited.perceivedExertion)
        assertFalse(activities.updateMetadata(999, ActivityMetadataUpdate(SportType.RUN, "x", "", Visibility.ONLY_ME, null)))

        assertTrue(activities.delete(id))
        assertFalse(activities.delete(id))
        assertNull(activities.observeActivity(id).first())
    }

    @Test
    fun `stats and personal bests across activities`() = runTest {
        val slow = activities.saveFinished(TestRecordings.steady(seconds = 1200, speedMps = 2.5), NewActivity("slow"), UnitSystem.METRIC)
        val fast = activities.saveFinished(
            TestRecordings.steady(seconds = 1200, speedMps = 3.5, startWallMillis = TestRecordings.START_WALL_MILLIS + 3_600_000L),
            NewActivity("fast"),
            UnitSystem.METRIC,
        )
        activities.saveFinished(TestRecordings.steady(SportType.RIDE, seconds = 600, speedMps = 8.0), NewActivity("ride"), UnitSystem.METRIC)

        val bests = activities.observePersonalBests(setOf(SportType.RUN)).first()
        val km = bests.single { it.target == 1000.0 }
        assertEquals(fast, km.activityId)
        assertTrue(bests.none { it.activityId == slow })

        val totals = activities.observeAllTimeTotals().first()
        assertEquals(2, totals.getValue(SportType.RUN).count)
        assertEquals(1, totals.getValue(SportType.RIDE).count)

        val week = activities.observeWeeklyTotals(2, sports = setOf(SportType.RUN)).first()
        assertEquals(2, week.sumOf { it.totals.count })

        val year = activities.observeYearTotals(2023).first()
        assertEquals(3, year.totals.count)

        val rideDurations = activities.observePersonalBests(setOf(SportType.RIDE), EffortKind.DURATION).first()
        assertTrue(rideDurations.isNotEmpty())
    }

    @Test
    fun `routes import with computed distance and elevation`() = runTest {
        val points = (0..100).map { RoutePointInput(47.0, 8.0 + it * 0.001, 500.0 + if (it < 50) it else 100 - it) }
        val id = routes.importRoute("  ", points, sport = SportType.RUN)
        val detail = routes.getRoute(id)!!
        assertEquals("Route", detail.route.name)
        assertEquals(101, detail.points.size)
        assertEquals(detail.points.last().distanceMeters, detail.route.distanceMeters, 1e-9)
        assertEquals(7_580.0, detail.route.distanceMeters, 20.0) // 0.1 degree of longitude at 47 N
        assertTrue(detail.route.elevationGainMeters in 40.0..50.0)
        assertEquals(SportType.RUN, detail.route.sport)
        assertEquals(detail, routes.observeRoute(id).first())

        assertTrue(routes.delete(id))
        assertNull(routes.getRoute(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a route needs two points`() = runTest {
        routes.importRoute("x", listOf(RoutePointInput(1.0, 1.0)))
    }
}
