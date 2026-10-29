// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.recording.EngineInput
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GpsStatusMonitorTest {
    private class FakeSource : LocationSource {
        var availability = LocationAvailability.AVAILABLE
        var fixes = Channel<EngineInput.Location>(Channel.UNLIMITED)

        override fun availability() = availability

        override fun locations(): Flow<EngineInput.Location> = fixes.consumeAsFlow()

        fun fix(accuracy: Double?) {
            fixes.trySend(EngineInput.Location(0L, 0L, 47.0, 8.0, accuracy))
        }
    }

    @Test
    fun `searching, then weak, then good, then stale`() = runTest {
        val source = FakeSource()
        val seen = mutableListOf<GpsStatus>()
        val job = launch { GpsStatusMonitor(source, staleAfter = 10.seconds).status().collect { seen += it } }
        runCurrent()
        source.fix(35.0)
        runCurrent()
        source.fix(8.0)
        runCurrent()
        advanceTimeBy(11_000)
        runCurrent()
        job.cancel()
        assertEquals(
            listOf(
                GpsStatus.SEARCHING,
                GpsStatus(GpsState.WEAK, 35.0),
                GpsStatus(GpsState.GOOD, 8.0),
                GpsStatus.SEARCHING,
            ),
            seen,
        )
    }

    @Test
    fun `missing permission is reported and picked up once granted`() = runTest {
        val source = FakeSource().apply { availability = LocationAvailability.NO_PERMISSION }
        val seen = mutableListOf<GpsStatus>()
        val job = launch { GpsStatusMonitor(source, recheckInterval = 2.seconds).status().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(GpsStatus(GpsState.NO_PERMISSION)), seen)

        source.availability = LocationAvailability.AVAILABLE
        advanceTimeBy(2_001)
        runCurrent()
        source.fix(5.0)
        runCurrent()
        job.cancel()
        assertEquals(
            listOf(GpsStatus(GpsState.NO_PERMISSION), GpsStatus.SEARCHING, GpsStatus(GpsState.GOOD, 5.0)),
            seen,
        )
    }

    @Test
    fun `location switched off while waiting is reported`() = runTest {
        val source = FakeSource()
        val seen = mutableListOf<GpsStatus>()
        val job = launch { GpsStatusMonitor(source, recheckInterval = 2.seconds).status().collect { seen += it } }
        runCurrent()
        source.availability = LocationAvailability.DISABLED
        advanceTimeBy(2_001)
        runCurrent()
        job.cancel()
        assertEquals(listOf(GpsStatus.SEARCHING, GpsStatus(GpsState.DISABLED)), seen)
    }

    @Test
    fun `location switched back on returns to searching`() = runTest {
        val source = FakeSource()
        val seen = mutableListOf<GpsStatus>()
        val job = launch { GpsStatusMonitor(source, recheckInterval = 2.seconds).status().collect { seen += it } }
        runCurrent()
        source.availability = LocationAvailability.DISABLED
        advanceTimeBy(2_001)
        runCurrent()
        source.availability = LocationAvailability.AVAILABLE
        advanceTimeBy(2_001)
        runCurrent()
        job.cancel()
        assertEquals(listOf(GpsStatus.SEARCHING, GpsStatus(GpsState.DISABLED), GpsStatus.SEARCHING), seen)
    }

    @Test
    fun `accuracy thresholds`() {
        assertEquals(GpsState.GOOD, GpsStatus.forAccuracy(20.0).state)
        assertEquals(GpsState.WEAK, GpsStatus.forAccuracy(20.1).state)
        assertEquals(GpsState.WEAK, GpsStatus.forAccuracy(null).state)
        assertEquals(GpsState.APPROXIMATE_ONLY, GpsStatus.unavailable(LocationAvailability.APPROXIMATE_ONLY).state)
    }
}
