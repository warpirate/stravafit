// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.PendingStart
import org.openactivity.core.recordingservice.needsGpsFix
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WidgetStateTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-11-01T01:30:00Z") // 07:00 IST
    private val nowMillis = now.toEpochMilli()
    private val elapsedNow = 5_000_000L

    private val idle = RecordingUiSnapshot.idle()
    private val running = idle.copy(
        status = RecordingStatus.RECORDING,
        sport = SportType.RUN,
        timerTime = 25.minutes + 2.seconds,
        distanceMeters = 5080.0,
        currentSpeedMps = 3.2,
        averageSpeedMps = 3.39,
    )

    private fun saved(endedAgo: kotlin.time.Duration, id: Long = 7) = SavedSummary(
        id = id,
        sport = SportType.RUN,
        title = "Morning Run",
        endTime = now.minusMillis(endedAgo.inWholeMilliseconds),
        zone = zone,
        movingTime = 38.minutes + 51.seconds,
        distanceMeters = 7890.0,
        averageSpeedMps = 3.38,
    )

    private fun resolve(
        snapshot: RecordingUiSnapshot = idle,
        pending: PendingStart? = null,
        interrupted: Boolean = false,
        lastSaved: SavedSummary? = null,
        dismissed: Long? = null,
        armedUntil: Long? = null,
    ) = resolveWidgetState(snapshot, pending, interrupted, lastSaved, dismissed, armedUntil, nowMillis, elapsedNow)

    @Test
    fun `nothing going on is Ready with the default sports`() {
        assertEquals(WidgetState.Ready(DEFAULT_SPORTS), resolve())
    }

    @Test
    fun `a widget start counting down shows the countdown`() {
        val state = resolve(pending = PendingStart(SportType.RIDE, null, elapsedNow + 3000))
        assertEquals(WidgetState.Countdown(SportType.RIDE, elapsedNow + 3000), state)
    }

    @Test
    fun `the countdown carries the GPS reading`() {
        val gps = GpsStatus.forAccuracy(38.0)
        val state = resolve(pending = PendingStart(SportType.RUN, null, elapsedNow + 3000, gps = gps)) as WidgetState.Countdown
        assertEquals(gps, state.gps)
    }

    @Test
    fun `a countdown that ended without a fix waits for GPS`() {
        val pending = PendingStart(SportType.RUN, null, elapsedNow - 1, gps = GpsStatus.SEARCHING, waitingForGps = true)
        assertEquals(WidgetState.WaitingForGps(SportType.RUN, GpsStatus.SEARCHING), resolve(pending = pending))
    }

    @Test
    fun `GPS sports need a fix to start, a weak one is enough`() {
        assertTrue(needsGpsFix(SportType.RUN, null))
        assertTrue(needsGpsFix(SportType.RUN, GpsStatus.SEARCHING))
        assertTrue(needsGpsFix(SportType.RIDE, GpsStatus(GpsState.DISABLED)))
        assertFalse(needsGpsFix(SportType.RUN, GpsStatus.forAccuracy(38.0)))
        assertFalse(needsGpsFix(SportType.WALK, GpsStatus.forAccuracy(6.0)))
        assertFalse(needsGpsFix(SportType.VIRTUAL_RIDE, null))
    }

    @Test
    fun `without location permission Ready says the start opens the app`() {
        val state = resolveWidgetState(idle, null, false, null, null, null, nowMillis, elapsedNow, canRecord = false)
        assertEquals(WidgetState.Ready(DEFAULT_SPORTS, canRecord = false), state)
    }

    @Test
    fun `render key follows the GPS state while counting down or waiting`() {
        val a = WidgetState.Countdown(SportType.RUN, elapsedNow, GpsStatus.SEARCHING)
        assertNotEquals(renderKey(a), renderKey(a.copy(gps = GpsStatus.forAccuracy(8.0))))
        val w = WidgetState.WaitingForGps(SportType.RUN, GpsStatus.SEARCHING)
        assertNotEquals(renderKey(w), renderKey(w.copy(gps = GpsStatus(GpsState.DISABLED))))
        assertNotEquals(renderKey(a), renderKey(w))
    }

    @Test
    fun `a live recording wins over a stale countdown`() {
        val state = resolve(snapshot = running, pending = PendingStart(SportType.RUN, null, elapsedNow))
        assertTrue(state is WidgetState.Recording)
    }

    @Test
    fun `recording maps the clock base so the Chronometer reads the timer`() {
        val state = resolve(snapshot = running) as WidgetState.Recording
        assertEquals(elapsedNow - (25.minutes + 2.seconds).inWholeMilliseconds, state.clockBaseElapsedRealtime)
        assertFalse(state.paused)
        assertEquals(1, state.lapNumber)
        assertEquals(3.2, state.currentSpeedMps, 0.0)
    }

    @Test
    fun `manual and auto pause both show as paused`() {
        assertTrue((resolve(snapshot = running.copy(status = RecordingStatus.PAUSED)) as WidgetState.Recording).paused)
        assertTrue((resolve(snapshot = running.copy(status = RecordingStatus.AUTO_PAUSED)) as WidgetState.Recording).paused)
    }

    @Test
    fun `lap number is completed laps plus one`() {
        val laps = List(5) { Lap(it, 0.seconds, 2.minutes, 2.minutes, Distance(400.0), Distance(0.0), LapTrigger.MANUAL) }
        assertEquals(6, (resolve(snapshot = running.copy(laps = laps)) as WidgetState.Recording).lapNumber)
    }

    @Test
    fun `finish is armed only inside the confirm window`() {
        assertTrue((resolve(snapshot = running, armedUntil = nowMillis + 1) as WidgetState.Recording).finishArmed)
        assertFalse((resolve(snapshot = running, armedUntil = nowMillis) as WidgetState.Recording).finishArmed)
        assertFalse((resolve(snapshot = running, armedUntil = null) as WidgetState.Recording).finishArmed)
    }

    @Test
    fun `an interrupted recording comes before a recent save`() {
        assertEquals(WidgetState.Interrupted, resolve(interrupted = true, lastSaved = saved(1.minutes)))
    }

    @Test
    fun `a fresh save shows Done until dismissed`() {
        assertTrue(resolve(lastSaved = saved(10.minutes)) is WidgetState.Done)
        assertEquals(WidgetState.Ready(DEFAULT_SPORTS), resolve(lastSaved = saved(10.minutes), dismissed = 7))
        assertTrue(resolve(lastSaved = saved(10.minutes, id = 8), dismissed = 7) is WidgetState.Done)
    }

    @Test
    fun `Done expires after six hours`() {
        val evening = Instant.parse("2026-11-01T14:30:00Z") // 20:00 IST, same day
        val ranAt1330 = SavedSummary(7, SportType.RUN, "Run", Instant.parse("2026-11-01T08:00:00Z"), zone, 30.minutes, 5000.0, 2.8)
        assertFalse(isFresh(ranAt1330, evening.toEpochMilli()))
        assertTrue(isFresh(ranAt1330, Instant.parse("2026-11-01T13:59:00Z").toEpochMilli()))
    }

    @Test
    fun `Done expires at local midnight even within six hours`() {
        val lateRun = SavedSummary(7, SportType.RUN, "Night Run", Instant.parse("2026-11-01T18:00:00Z"), zone, 30.minutes, 5000.0, 2.8) // 23:30 IST
        assertTrue(isFresh(lateRun, Instant.parse("2026-11-01T18:20:00Z").toEpochMilli())) // 23:50 IST
        assertFalse(isFresh(lateRun, Instant.parse("2026-11-01T18:40:00Z").toEpochMilli())) // 00:10 IST next day
    }

    @Test
    fun `Done carries the unit system`() {
        val state = resolve(snapshot = idle.copy(units = UnitSystem.IMPERIAL), lastSaved = saved(1.minutes)) as WidgetState.Done
        assertEquals(UnitSystem.IMPERIAL, state.units)
    }

    @Test
    fun `render key ignores sub-step changes and catches real ones`() {
        val a = resolve(snapshot = running) as WidgetState.Recording
        assertEquals(renderKey(a), renderKey(a.copy(distanceMeters = 5085.0, clockBaseElapsedRealtime = a.clockBaseElapsedRealtime + 300)))
        assertNotEquals(renderKey(a), renderKey(a.copy(distanceMeters = 5091.0)))
        assertNotEquals(renderKey(a), renderKey(a.copy(timer = a.timer + 5.seconds)))
        assertNotEquals(renderKey(a), renderKey(a.copy(paused = true)))
        assertNotEquals(renderKey(a), renderKey(a.copy(finishArmed = true)))
        assertNotEquals(renderKey(a), renderKey(a.copy(lapNumber = 2)))
    }

    @Test
    fun `a paused clock base drifting with the wall clock is not a change`() {
        val paused = (resolve(snapshot = running.copy(status = RecordingStatus.PAUSED)) as WidgetState.Recording)
        assertEquals(renderKey(paused), renderKey(paused.copy(clockBaseElapsedRealtime = paused.clockBaseElapsedRealtime + 60_000)))
    }

    @Test
    fun `six hours is the Done lifetime`() {
        assertEquals(6.hours, DONE_TTL)
    }
}
