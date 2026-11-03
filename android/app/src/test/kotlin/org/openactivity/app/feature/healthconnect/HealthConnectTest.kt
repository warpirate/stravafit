// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.app.testing.FakeHealthConnectGateway
import org.openactivity.app.testing.FakeHealthConnectJobs
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.InMemoryPreferencesDataStore
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ImportedPoint
import org.openactivity.core.healthconnect.HealthConnectAvailability
import org.openactivity.core.healthconnect.HealthHeartRate
import org.openactivity.core.healthconnect.HealthLocation
import org.openactivity.core.healthconnect.HealthPermissions
import org.openactivity.core.healthconnect.HealthWorkoutRead
import org.openactivity.core.healthconnect.SyncDirection
import org.openactivity.core.healthconnect.SyncSwitchState
import org.openactivity.feature.activitydetail.fileimport.ActivityImportSaver
import org.openactivity.feature.activitydetail.fileimport.ExternalImport
import org.openactivity.feature.activitydetail.fileimport.SaveOutcome
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Health Connect sync, writer and screen over a real (in-memory) database and a fake Health Connect. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthConnectTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = Instant.parse("2026-11-03T06:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Asia/Kolkata"))
    private lateinit var db: OpenActivityDatabase
    private lateinit var activities: LocalActivityRepository
    private lateinit var saver: ActivityImportSaver
    private val preferences = LocalPreferences(InMemoryPreferencesDataStore())
    private val gateway = FakeHealthConnectGateway()
    private val jobs = FakeHealthConnectJobs()

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        activities = LocalActivityRepository(db.activityDao(), clock)
        saver = ActivityImportSaver(activities, FakeSettingsRepository())
    }

    @After
    fun close() = db.close()

    /**
     * Lets the view model's work finish: some of it (the activity conversion) runs on real threads that
     * [advanceUntilIdle] does not wait for.
     */
    private fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            advanceUntilIdle()
            if (done()) return
            Thread.sleep(10)
        }
        advanceUntilIdle()
    }

    private fun sync() = HealthConnectSync(gateway, saver, preferences, clock, OWN)

    private fun writer() = HealthConnectWriter(activities, gateway, preferences)

    private fun garminRun(id: String, start: Instant, origin: String = GARMIN, withRoute: Boolean = true) = HealthWorkoutRead(
        id = id,
        originPackage = origin,
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        title = "Pune Running",
        notes = null,
        start = start,
        end = start.plusSeconds(600),
        startOffset = ZoneOffset.ofHoursMinutes(5, 30),
        route = if (withRoute) List(61) { i -> HealthLocation(start.plusSeconds(i * 10L), 18.5368 + i * 4e-5, 73.8791, 560.0) } else null,
        routeConsentRequired = !withRoute,
        heartRate = List(61) { i -> HealthHeartRate(start.plusSeconds(i * 10L), 140L + i % 5) },
        distanceMeters = 2_700.0,
    )

    /** A run recorded in this app, two minutes long, along the river in Pune. */
    private suspend fun recordRun(start: Instant, uuid: String = "rec-1"): Long {
        val points = List(25) { i ->
            ImportedPoint(18.5368 + i * 5e-5, 73.8791, 560.0, start.plusSeconds(i * 5L), heartRate = 150 + i % 3)
        }
        val imported = ImportedActivity("rec", ImportFormat.GPX, "Lunch Run", SportType.RUN, true, start, points, 130.0, 120, null, uuid, uuid)
        return (saver.saveExternal(imported, ExternalImport(uuid, "Lunch Run"), saver.existingStarts()) as SaveOutcome.Saved).activityId
    }

    @Test
    fun `reading imports other apps' workouts once, skipping our own`() = runTest {
        preferences.setHealthReadEnabled(true)
        gateway.allowAll()
        gateway.stored += garminRun("a", now.minusSeconds(86_400))
        gateway.stored += garminRun("mine", now.minusSeconds(40_000), origin = OWN)
        gateway.stored += garminRun("indoor", now.minusSeconds(20_000), withRoute = false)

        val first = sync().sync(inBackground = false)
        assertEquals(2, first.imported)
        assertEquals(1, first.withoutMap)
        val list = activities.observeActivities().first()
        assertEquals(setOf("hc:a", "hc:indoor"), list.map { it.uuid }.toSet())
        val run = list.first { it.uuid == "hc:a" }
        assertEquals("Pune Running", run.title)
        assertEquals(SportType.RUN, run.sport)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), run.timeZone)
        assertNotNull(run.polyline)
        assertNotNull(run.averageHeartRate)
        val indoor = list.first { it.uuid == "hc:indoor" }
        assertNull(indoor.polyline)
        assertEquals(2_700.0, indoor.distanceMeters, 1e-9)

        // The next read overlaps the last one: nothing is imported twice.
        val second = sync().sync(inBackground = false)
        assertEquals(0, second.imported)
        assertEquals(2, second.duplicates)
        assertEquals(2, activities.observeActivities().first().size)
        assertEquals(now.toEpochMilli(), preferences.healthLastSyncMillis.first())
        assertEquals(now.minus(HealthConnectSync.OVERLAP), gateway.reads.last().first)
    }

    @Test
    fun `a watch's copy of a run recorded here is a duplicate`() = runTest {
        preferences.setHealthReadEnabled(true)
        gateway.allowAll()
        val start = now.minusSeconds(7_200)
        recordRun(start)
        gateway.stored += garminRun("watch", start.plusSeconds(20))
        val result = sync().sync(inBackground = false)
        assertEquals(0, result.imported)
        assertEquals(1, result.duplicates)
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `reading needs the switch, health connect and its permission`() = runTest {
        assertEquals(SyncResult.Skipped.SWITCHED_OFF, sync().sync(false).skipped)
        preferences.setHealthReadEnabled(true)
        assertEquals(SyncResult.Skipped.NOT_ALLOWED, sync().sync(false).skipped)
        gateway.allowAll()
        // The daily job reads only with the background permission, which this Health Connect lacks.
        assertEquals(SyncResult.Skipped.BACKGROUND_NOT_ALLOWED, sync().sync(true).skipped)
        gateway.availability = HealthConnectAvailability.NOT_INSTALLED
        assertEquals(SyncResult.Skipped.NOT_AVAILABLE, sync().sync(false).skipped)
        assertTrue(gateway.reads.isEmpty())
    }

    @Test
    fun `new activities are written once writing is on, imported ones are not written back`() = runTest {
        preferences.setHealthWriteEnabled(true)
        gateway.allowAll()
        writer().markCurrent()
        val id = recordRun(now.minusSeconds(3_600))
        val fromHc = garminRun("x", now.minusSeconds(90_000))
        preferences.setHealthReadEnabled(true)
        gateway.stored += fromHc
        sync().sync(false)
        assertEquals(1, writer().writeNew())
        val written = gateway.written.values.single()
        assertEquals("rec-1", written.clientId)
        assertEquals(SportType.RUN, written.sport)
        assertEquals(activities.observeActivity(id).first()!!.startTime, written.start)
        assertEquals(25, written.route.size)
        assertEquals(25, written.heartRate.size)
        assertEquals(150L, written.heartRate.first().beatsPerMinute)
        // Nothing new: nothing written again.
        assertEquals(0, writer().writeNew())
    }

    @Test
    fun `writing past activities sends everything except what came from health connect`() = runTest {
        recordRun(now.minusSeconds(90_000), uuid = "rec-old")
        recordRun(now.minusSeconds(3_600), uuid = "rec-new")
        preferences.setHealthReadEnabled(true)
        gateway.allowAll()
        gateway.stored += garminRun("g", now.minusSeconds(50_000))
        sync().sync(false)
        val summary = writer().writeAll()
        assertEquals(WriteSummary(2, 0), summary)
        assertEquals(setOf("rec-old", "rec-new"), gateway.written.keys)
        // Again: the same two client ids, updated rather than doubled.
        writer().writeAll()
        assertEquals(2, gateway.written.size)
    }

    @Test
    fun `turning writing on asks for permission, and a denial keeps it off`() = runTest {
        val vm = HealthConnectViewModel(gateway, preferences, writer(), sync(), jobs)
        val effects = mutableListOf<HealthConnectEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setWriteEnabled(true)
        advanceUntilIdle()
        val request = effects.single() as HealthConnectEffect.RequestPermissions
        assertEquals(SyncDirection.WRITE, request.direction)
        assertEquals(HealthPermissions.forWriting, request.permissions)

        // The user says no.
        vm.onPermissionsResult(SyncDirection.WRITE, emptySet())
        advanceUntilIdle()
        assertFalse(preferences.healthWriteEnabled.first())
        assertTrue(vm.uiState.value.message!!.contains("didn't allow"))
        vm.messageShown()

        // The user allows exercise but not the route.
        gateway.granted = setOf(HealthPermissions.WRITE_EXERCISE, HealthPermissions.HEART_RATE_WRITE, HealthPermissions.DISTANCE_WRITE)
        vm.setWriteEnabled(true)
        advanceUntilIdle()
        vm.onPermissionsResult(SyncDirection.WRITE, gateway.granted)
        settle { vm.uiState.value.message != null }
        assertTrue(preferences.healthWriteEnabled.first())
        assertEquals("On, without map.", vm.uiState.value.message)
        assertEquals(SyncSwitchState.ON, vm.uiState.value.status!!.switchState(SyncDirection.WRITE, true))
        // Only activities saved from now on are written automatically.
        assertNotNull(preferences.healthWriteWatermark.first())
    }

    @Test
    fun `a permission revoked in health connect shows on the switch`() = runTest {
        preferences.setHealthReadEnabled(true)
        gateway.allowAll()
        val vm = HealthConnectViewModel(gateway, preferences, writer(), sync(), jobs)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(SyncSwitchState.ON, vm.uiState.value.status!!.switchState(SyncDirection.READ, vm.uiState.value.readEnabled))

        gateway.granted = emptySet()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(SyncSwitchState.NEEDS_PERMISSION, vm.uiState.value.status!!.switchState(SyncDirection.READ, vm.uiState.value.readEnabled))

        vm.syncNow()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.message!!.contains("no longer allows reading"))
    }

    @Test
    fun `turning reading on schedules the daily read and syncs straight away`() = runTest {
        gateway.allowAll()
        gateway.stored += garminRun("a", now.minusSeconds(86_400))
        val vm = HealthConnectViewModel(gateway, preferences, writer(), sync(), jobs)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.setReadEnabled(true)
        settle { vm.uiState.value.message != null }
        assertTrue(preferences.healthReadEnabled.first())
        assertTrue(jobs.scheduled)
        assertEquals("Imported 1 activity.", vm.uiState.value.message)
        vm.setReadEnabled(false)
        advanceUntilIdle()
        assertFalse(jobs.scheduled)
    }

    @Test
    fun `missing health connect makes both switches unavailable`() = runTest {
        gateway.availability = HealthConnectAvailability.NOT_INSTALLED
        val vm = HealthConnectViewModel(gateway, preferences, writer(), sync(), jobs)
        val effects = mutableListOf<HealthConnectEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val status = vm.uiState.value.status!!
        assertEquals(HealthConnectAvailability.NOT_INSTALLED, status.availability)
        assertEquals(SyncSwitchState.UNAVAILABLE, status.switchState(SyncDirection.WRITE, false))
        vm.setWriteEnabled(true)
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        assertFalse(preferences.healthWriteEnabled.first())
    }

    private companion object {
        const val OWN = "org.openactivity.app.debug"
        const val GARMIN = "com.garmin.android.apps.connectmobile"
    }
}
