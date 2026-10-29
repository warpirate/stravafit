// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.geo.Spherical
import org.openactivity.metrics.ElevationMetrics
import org.openactivity.model.LapTrigger
import org.openactivity.model.SportType
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RecordingEngineTest {
    private fun RecordingEngine.feed(f: Feed): RecordingState = processAll(f.inputs)

    private fun assertNear(expected: Double, actual: Double, tolerance: Double, what: String = "") =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$what expected $expected ±$tolerance, got $actual")

    @Test
    fun `idle fixes only report gps quality`() {
        val e = engine(SportType.RUN)
        val f = Feed()
        assertEquals(GpsQuality.NONE, e.state.gpsQuality)
        assertEquals(GpsQuality.GOOD, e.process(f.fix(accuracy = 5.0)).gpsQuality)
        assertEquals(GpsQuality.FAIR, e.process(f.fix(accuracy = 35.0).copy(monotonicNanos = f.nanos(1.0))).gpsQuality)
        assertEquals(GpsQuality.POOR, e.process(f.fix(accuracy = 80.0).copy(monotonicNanos = f.nanos(2.0))).gpsQuality)
        val s = e.process(EngineInput.Tick(f.nanos(20.0)))
        assertEquals(GpsQuality.NONE, s.gpsQuality)
        assertEquals(RecordingStatus.IDLE, s.status)
        assertEquals(0.0, s.distanceMeters)
        assertEquals(0.seconds, s.elapsed)
    }

    @Test
    fun `elapsed and moving time follow the monotonic clock`() {
        val e = engine(SportType.RUN)
        val live = e.feed(Feed().action(Action.START).move(60, 3.0))
        assertEquals(RecordingStatus.RECORDING, live.status)
        assertEquals(60.seconds, live.elapsed)
        assertNear(3.0, live.currentSpeedMps, 0.01, "current speed")
        assertNear(1000.0 / 3.0, live.currentPaceSecondsPerKm!!, 1.0, "pace")
        val f = Feed().action(Action.START).move(60, 3.0).action(Action.FINISH)
        val done = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        assertEquals(60.seconds, done.elapsedTime)
        assertNear(59.0, done.movingTime.inWholeMilliseconds / 1000.0, 2.0, "moving")
        assertNear(177.0, done.distanceMeters, 2.0, "distance")
        assertTrue(done.movingTime <= done.elapsedTime)
        assertEquals(1, done.laps.size)
        assertEquals(LapTrigger.SESSION_END, done.laps.single().trigger)
    }

    @Test
    fun `manual pause dominates auto-pause and is never auto-resumed`() {
        val e = engine(SportType.RIDE) // auto-pause defaults to on for rides
        val f = Feed().action(Action.START).move(30, 8.0).move(15, 0.0)
        assertEquals(RecordingStatus.AUTO_PAUSED, e.feed(f).status)
        e.process(EngineInput.UserAction(f.nanos(f.seconds), Action.PAUSE))
        assertEquals(RecordingStatus.PAUSED, e.state.status)
        val before = e.state.distanceMeters
        val g = f.continueAt(f.current())
        g.move(20, 8.0)
        e.feed(g)
        assertEquals(RecordingStatus.PAUSED, e.state.status, "moving must not resume a manual pause")
        assertEquals(before, e.state.distanceMeters)
        e.process(EngineInput.UserAction(g.nanos(g.seconds), Action.RESUME))
        assertEquals(RecordingStatus.RECORDING, e.state.status)
        g.inputs.clear()
        g.move(10, 8.0)
        e.feed(g)
        assertTrue(e.state.distanceMeters > before + 50.0)
        e.process(EngineInput.UserAction(g.nanos(g.seconds), Action.FINISH))
        val kinds = e.finishedActivity!!.pauses.map { it.kind }
        assertEquals(listOf(PauseKind.AUTO, PauseKind.MANUAL), kinds)
        assertEquals(1, e.finishedActivity!!.autoPauseCount)
    }

    @Test
    fun `auto-pause is back-dated to the actual stop`() {
        val f = Feed().action(Action.START).move(60, 8.0).move(30, 0.0).move(60, 8.0).action(Action.FINISH)
        val done = engine(SportType.RIDE).apply { feed(f) }.finishedActivity!!
        val pause = done.pauses.single()
        assertEquals(PauseKind.AUTO, pause.kind)
        assertNear(30.0, pause.durationNanos / 1e9, 2.0, "pause length")
        assertNear(120.0, done.movingTime.inWholeMilliseconds / 1000.0, 2.0, "moving")
        assertNear(119.0 * 8.0, done.distanceMeters, 10.0, "distance")
    }

    @Test
    fun `auto-resume is suppressed while accuracy is poor`() {
        val e = engine(SportType.RIDE)
        val f = Feed().action(Action.START).move(20, 8.0).move(15, 0.0).move(10, 8.0, accuracy = 40.0)
        assertEquals(RecordingStatus.AUTO_PAUSED, e.feed(f).status)
        f.inputs.clear()
        f.move(5, 8.0, accuracy = 5.0)
        assertEquals(RecordingStatus.RECORDING, e.feed(f).status)
    }

    @Test
    fun `laps including a zero-length lap`() {
        val f = Feed().action(Action.START).move(100, 3.0).action(Action.LAP).action(Action.LAP)
            .move(100, 3.0).action(Action.LAP).action(Action.FINISH)
        val done = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        assertEquals(3, done.laps.size, "a lap pressed at the very end must not add an empty lap")
        assertNear(297.0, done.laps[0].distance.meters, 3.0, "lap 1")
        assertTrue(done.laps[1].isZeroLength)
        assertEquals(0.0, done.laps[1].distance.meters)
        assertNear(300.0, done.laps[2].distance.meters, 3.0, "lap 3")
        assertEquals(listOf(LapTrigger.MANUAL, LapTrigger.MANUAL, LapTrigger.MANUAL), done.laps.map { it.trigger })
        assertEquals(listOf(0, 1, 2), done.laps.map { it.index })
    }

    @Test
    fun `discard ends the session and ignores later input`() {
        val e = engine(SportType.RUN)
        e.feed(Feed().action(Action.START).move(10, 3.0).action(Action.DISCARD))
        assertEquals(RecordingStatus.DISCARDED, e.state.status)
        assertNull(e.finishedActivity)
        e.process(EngineInput.UserAction(Long.MAX_VALUE / 2, Action.START))
        assertEquals(RecordingStatus.DISCARDED, e.state.status)
    }

    @Test
    fun `input after finish is ignored`() {
        val e = engine(SportType.RUN)
        val f = Feed().action(Action.START).move(30, 3.0).action(Action.FINISH)
        e.feed(f)
        val done = e.finishedActivity
        f.inputs.clear()
        f.move(30, 3.0)
        e.feed(f)
        assertEquals(done, e.finishedActivity)
        assertEquals(RecordingStatus.FINISHED, e.state.status)
        assertEquals(done!!.distanceMeters, e.state.distanceMeters)
    }

    @Test
    fun `wall clock jumps change nothing but labels`() {
        val f = Feed().action(Action.START).move(120, 3.0).action(Action.FINISH)
        val clean = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        val jumped = f.inputs.map {
            if (it is EngineInput.Location) {
                val s = (it.monotonicNanos - f.baseNanos) / 1e9
                val delta = (if (s >= 40) 3_600_000L else 0L) + (if (s >= 80) -90_000L else 0L)
                it.copy(wallClockMillis = it.wallClockMillis + delta)
            } else {
                it
            }
        }
        val result = RecordingEngine(RecordingConfig(SportType.RUN)).apply { processAll(jumped) }.finishedActivity!!
        assertEquals(clean, result)
        assertTrue(result.points.zipWithNext().all { (a, b) -> b.epochMillis >= a.epochMillis })
    }

    @Test
    fun `shifting the monotonic clock changes nothing`() {
        val f = Feed().action(Action.START).move(90, 3.0).move(20, 0.0).move(60, 3.5).action(Action.FINISH)
        val a = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        val shifted = f.inputs.map { shift(it, 987_654_321_000L) }
        val b = engine(SportType.RUN).apply { processAll(shifted) }.finishedActivity!!
        assertEquals(a, b)
    }

    private fun shift(i: EngineInput, d: Long): EngineInput = when (i) {
        is EngineInput.Location -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.Tick -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.UserAction -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.Pressure -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.HeartRate -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.Power -> i.copy(monotonicNanos = i.monotonicNanos + d)
        is EngineInput.Cadence -> i.copy(monotonicNanos = i.monotonicNanos + d)
    }

    @Test
    fun `duplicate and out-of-order fixes are dropped`() {
        val f = Feed().action(Action.START).move(60, 3.0).action(Action.FINISH)
        val clean = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        val locs = f.inputs.filterIsInstance<EngineInput.Location>()
        val noisy = ArrayList(f.inputs)
        noisy.add(noisy.indexOf(locs[10]) + 1, locs[10].copy(latitude = locs[10].latitude + 0.001))
        noisy.add(noisy.indexOf(locs[20]) + 1, locs[5])
        val result = engine(SportType.RUN).apply { processAll(noisy) }.finishedActivity!!
        assertEquals(clean, result)
    }

    @Test
    fun `distance starts at the first fix with usable accuracy`() {
        val f = Feed().action(Action.START).move(20, 3.0, accuracy = 80.0).move(100, 3.0).action(Action.FINISH)
        val done = engine(SportType.RUN).apply { feed(f) }.finishedActivity!!
        assertNear(99.0 * 3.0, done.distanceMeters, 3.0, "distance")
        assertEquals(120.seconds, done.elapsedTime)
    }

    @Test
    fun `single teleport is rejected and a persistent jump starts a new segment`() {
        val base = Feed().action(Action.START).move(60, 3.0).action(Action.FINISH)
        val clean = engine(SportType.RUN).apply { feed(base) }.finishedActivity!!
        val withJump = ArrayList(base.inputs)
        val victim = withJump.filterIsInstance<EngineInput.Location>()[30]
        val far = Spherical.destination(org.openactivity.model.LatLng(victim.latitude, victim.longitude), 0.0, 500.0)
        withJump[withJump.indexOf(victim)] = victim.copy(latitude = far.latitude, longitude = far.longitude)
        val jumped = engine(SportType.RUN).apply { processAll(withJump) }.finishedActivity!!
        assertNear(clean.distanceMeters, jumped.distanceMeters, 3.5, "distance with one outlier")

        val f = Feed().action(Action.START).move(30, 3.0)
        val train = f.continueAt(Spherical.destination(f.current(), 0.0, 2_000.0), afterSeconds = 30.0)
        train.move(40, 3.0)
        val e = engine(SportType.RUN)
        e.processAll(f.inputs + train.inputs + EngineInput.UserAction(train.nanos(train.seconds), Action.FINISH))
        val done = e.finishedActivity!!
        assertTrue(done.distanceMeters < 250.0, "no 2 km straight line: ${done.distanceMeters}")
        assertTrue(done.points.last().segment > 0)
    }

    @Test
    fun `standing still with jitter adds no distance`() {
        val rnd = Random(9)
        val f = Feed().action(Action.START)
        val home = f.current()
        val e = engine(SportType.RUN, autoPause = false)
        e.processAll(f.inputs)
        for (s in 1..600) {
            val p = Spherical.destination(home, rnd.nextDouble(0.0, 360.0), rnd.nextDouble(0.0, 6.0))
            e.process(f.fix(accuracy = 8.0, speed = rnd.nextDouble(0.0, 0.15), at = p).copy(monotonicNanos = f.nanos(s.toDouble())))
        }
        e.process(EngineInput.UserAction(f.nanos(601.0), Action.FINISH))
        val done = e.finishedActivity!!
        assertTrue(done.distanceMeters < 10.0, "jitter distance ${done.distanceMeters}")
        assertTrue(done.movingTime.inWholeSeconds < 10, "moving ${done.movingTime}")
    }

    @Test
    fun `receiver reporting zero speed while moving does not freeze distance`() {
        val f = Feed().action(Action.START)
        val e = engine(SportType.RUN)
        e.processAll(f.inputs)
        repeat(300) {
            f.inputs.clear()
            f.move(1, 3.0)
            e.processAll(f.inputs.map { if (it is EngineInput.Location) it.copy(speedMps = 0.0) else it })
        }
        e.process(EngineInput.UserAction(f.nanos(f.seconds), Action.FINISH))
        val done = e.finishedActivity!!
        assertNear(299.0 * 3.0, done.distanceMeters, 0.1 * 897.0, "distance")
    }

    @Test
    fun `tunnel gap is bridged with a straight line and flagged`() {
        val f = Feed(bearing = 200.0).action(Action.START).move(60, 15.0)
        val inTunnel = f.continueAt(Spherical.destination(f.current(), 200.0, 140 * 15.0))
        inTunnel.ticks(140)
        inTunnel.move(60, 15.0)
        val e = engine(SportType.RIDE)
        e.processAll(f.inputs + inTunnel.inputs + EngineInput.UserAction(inTunnel.nanos(inTunnel.seconds), Action.FINISH))
        val done = e.finishedActivity!!
        assertNear((59 + 141 + 59) * 15.0, done.distanceMeters, 30.0, "distance")
        assertEquals(1, done.gpsGaps.size)
        assertNear(141 * 15.0, done.gpsGaps.single().bridgedMeters, 20.0, "bridge")
        assertTrue(ActivityFlag.SIGNIFICANT_GPS_GAP in done.flags)
        assertEquals(0, done.autoPauseCount, "no fixes is not a stop")
        assertEquals(260.seconds, done.movingTime)
    }

    @Test
    fun `indoor workout without gps counts timer time`() {
        val f = Feed().action(Action.START)
        repeat(600) { i ->
            f.ticks(1)
            f.add(EngineInput.HeartRate(f.nanos(f.seconds), if (i < 300) 120 else 140))
        }
        f.action(Action.FINISH)
        val done = engine(SportType.WORKOUT).apply { feed(f) }.finishedActivity!!
        assertEquals(600.seconds, done.elapsedTime)
        assertEquals(600.seconds, done.movingTime)
        assertEquals(0.0, done.distanceMeters)
        assertNear(130.0, done.averageHeartRate!!, 0.5, "avg hr")
        assertEquals(140, done.maxHeartRate)
        assertTrue(done.points.isEmpty())
        assertTrue(done.bestEfforts.isEmpty())
    }

    @Test
    fun `barometer drives elevation`() {
        val f = Feed().action(Action.START)
        val e = engine(SportType.HIKE)
        e.processAll(f.inputs)
        f.inputs.clear()
        for (s in 1..300) {
            f.move(1, 1.0)
            f.add(EngineInput.Pressure(f.nanos(f.seconds) + 2, ElevationMetrics.altitudeToPressure(500.0 + s / 6.0)))
        }
        f.action(Action.FINISH)
        e.feed(f)
        val done = e.finishedActivity!!
        assertEquals(AltitudeSource.BAROMETER, done.altitudeSource)
        assertNear(50.0, done.elevationGainMeters, 3.0, "gain")
        assertEquals(0.0, done.elevationLossMeters)
        assertNotNull(e.state.elevation.currentMeters)
    }

    @Test
    fun `mock locations flag the activity`() {
        val f = Feed().action(Action.START).move(10, 3.0)
        f.add(f.fix(speed = 3.0, mock = true).copy(monotonicNanos = f.nanos(10.5)))
        f.move(5, 3.0).action(Action.FINISH)
        val e = engine(SportType.RUN)
        e.feed(f)
        assertTrue(e.state.isMockLocation)
        assertTrue(ActivityFlag.MOCK_LOCATION in e.finishedActivity!!.flags)
    }

    @Test
    fun `sensor readings go stale`() {
        val f = Feed().action(Action.START)
        val e = engine(SportType.RIDE)
        e.feed(f)
        e.process(EngineInput.HeartRate(f.nanos(1.0), 150))
        e.process(EngineInput.Power(f.nanos(1.0), 200))
        e.process(EngineInput.Cadence(f.nanos(1.0), 90.0))
        assertEquals(150, e.state.heartRateBpm)
        assertEquals(200, e.state.powerWatts)
        assertEquals(90.0, e.state.cadence)
        e.process(EngineInput.Tick(f.nanos(10.0)))
        assertNull(e.state.heartRateBpm)
        assertNull(e.state.powerWatts)
        assertNull(e.state.cadence)
    }
}
