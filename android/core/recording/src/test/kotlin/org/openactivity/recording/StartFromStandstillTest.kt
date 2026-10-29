// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.openactivity.model.SportType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression for the e2e replay: the athlete holds still at the start, taps Start, then runs at 2.79 m/s
 * while the receiver reports a Doppler speed of 0 (or none). The first stretch of movement used to be
 * added as one late straight-line bridge, counting as distance but not as moving time, so the pace ran fast.
 */
class StartFromStandstillTest {
    private fun near(expected: Double, actual: Double, tol: Double, what: String) =
        assertTrue(abs(expected - actual) <= tol, "$what expected $expected ±$tol, got $actual")

    private fun run(speedReport: Double?) {
        val f = Feed().action(Action.START)
        val e = engine(SportType.RUN)
        // Held still at the start for 10 s after tapping Start.
        repeat(10) {
            f.ticks(1)
            f.add(f.fix(speed = speedReport))
        }
        e.processAll(f.inputs)
        val moveStart = f.seconds
        fun step(n: Int) = repeat(n) {
            f.inputs.clear()
            f.move(1, 2.79)
            e.processAll(f.inputs.map { if (it is EngineInput.Location) it.copy(speedMps = speedReport) else it })
        }
        step(25)
        val live25 = e.state
        near(25 * 2.79, live25.distanceMeters, 15.0, "distance after 25 s")
        assertTrue(live25.movingTime.inWholeSeconds >= 18, "moving after 25 s of running: ${live25.movingTime}")
        step(375)
        val live = e.state
        val runSeconds = f.seconds - moveStart
        near(runSeconds, live.movingTime.inWholeMilliseconds / 1000.0, 6.0, "live moving")
        e.process(EngineInput.UserAction(f.nanos(f.seconds), Action.FINISH))
        val done = e.finishedActivity!!
        near(runSeconds, done.movingTime.inWholeMilliseconds / 1000.0, 6.0, "final moving")
        near(1000.0 / 2.79, 1000.0 / done.averageSpeedMps, 8.0, "pace")
        val split = done.splits.first()
        val splitPace = split.movingTime.inWholeMilliseconds / 1000.0 / (split.distance.meters / 1000.0)
        val effort = done.bestEfforts.first { it.targetMeters == 1000.0 }
        assertTrue(splitPace >= effort.elapsedSeconds - 2.0, "split $splitPace faster than best 1K ${effort.elapsedSeconds}")
    }

    @Test
    fun `receiver reports zero speed`() = run(0.0)

    @Test
    fun `receiver reports no speed`() = run(null)
}

class LapAndElevationRegressionTest {
    @Test
    fun `lap timer stands still while paused`() {
        val f = Feed().action(Action.START).move(30, 3.0).action(Action.PAUSE).ticks(27)
        val s = engine(SportType.RUN).processAll(f.inputs)
        assertTrue(s.currentLap.elapsed == s.timerTime, "lap ${s.currentLap.elapsed} timer ${s.timerTime}")
    }

    @Test
    fun `stale altitude at the start is replaced and adds no gain`() {
        // The receiver reports a stale 550.6 m for the first minute, then the real 570 m, then climbs 10 m
        // and descends 18 m.
        val f = Feed().action(Action.START)
        val e = engine(SportType.RUN)
        e.processAll(f.inputs)
        fun stepAt(alt: Double) {
            f.inputs.clear()
            f.move(1, 2.79)
            e.processAll(f.inputs.map { if (it is EngineInput.Location) it.copy(altitudeMeters = alt) else it })
        }
        repeat(60) { stepAt(550.6) }
        repeat(30) { stepAt(570.0) }
        repeat(100) { stepAt(570.0 + it * 0.1) }
        val live = e.state
        assertTrue(live.elevation.gainMeters <= 11.0, "gain ${live.elevation.gainMeters}")
        repeat(180) { stepAt(580.0 - it * 0.1) }
        e.process(EngineInput.UserAction(f.nanos(f.seconds), Action.FINISH))
        val done = e.finishedActivity!!
        val alts = done.points.mapNotNull { it.altitudeMeters }
        assertTrue(alts.min() >= 561.0, "min ${alts.min()}")
        assertTrue(done.elevationGainMeters <= 11.0, "gain ${done.elevationGainMeters}")
        assertTrue(alts.max() >= 578.0, "max ${alts.max()}")
    }

    @Test
    fun `a barometer that never moves does not pin the altitude`() {
        val f = Feed().action(Action.START)
        val e = engine(SportType.RUN)
        e.processAll(f.inputs)
        for (sec in 1..100) {
            f.inputs.clear()
            f.move(1, 2.79)
            f.add(EngineInput.Pressure(f.nanos(f.seconds) + 2, 950.0))
            e.processAll(f.inputs.map { if (it is EngineInput.Location) it.copy(altitudeMeters = 570.0 + sec * 0.1) else it })
        }
        assertEquals(AltitudeSource.GPS, e.state.elevation.source)
        assertTrue(e.state.elevation.currentMeters!! > 578.0, "altitude ${e.state.elevation.currentMeters}")
    }
}
