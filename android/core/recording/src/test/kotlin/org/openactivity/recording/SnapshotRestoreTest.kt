// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.openactivity.geo.Spherical
import org.openactivity.model.SportType
import org.openactivity.recording.corpus.Replay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SnapshotRestoreTest {
    @TestFactory
    fun `restoring a snapshot continues identically`(): List<DynamicTest> =
        Replay.manifest().filter { it.id in setOf("S006", "S023", "S028", "S029") }.map { entry ->
            DynamicTest.dynamicTest(entry.id) {
                val track = Replay.loadTrack(entry)
                val inputs = Replay.inputsWithTicks(track)
                val reference = RecordingEngine(Replay.configFor(track)).apply { processAll(inputs) }.finishedActivity
                for (cut in listOf(inputs.size / 3, inputs.size / 2, inputs.size * 4 / 5)) {
                    val first = RecordingEngine(Replay.configFor(track))
                    first.processAll(inputs.subList(0, cut))
                    val snapshot = first.snapshot()
                    val restored = RecordingEngine.restore(snapshot)
                    assertEquals(snapshot, restored.snapshot(), "restore must reproduce the snapshot exactly")
                    assertEquals(first.state, restored.state)
                    restored.processAll(inputs.subList(cut, inputs.size))
                    assertEquals(reference, restored.finishedActivity, "${entry.id} cut at $cut")
                }
            }
        }

    @Test
    fun `process death leaves a gap that adds no distance or moving time`() {
        val before = Feed().action(Action.START).move(60, 3.0)
        val engine = engine(SportType.RUN)
        engine.processAll(before.inputs)
        val snapshot = engine.snapshot()
        // The process is dead for 120 s while the athlete keeps running 360 m.
        val after = before.continueAt(Spherical.destination(before.current(), 90.0, 360.0), afterSeconds = 120.0)
        val restored = RecordingEngine.restore(snapshot, Interruption(after.nanos(after.seconds), null))
        after.move(60, 3.0).action(Action.FINISH)
        restored.processAll(after.inputs)
        val done = restored.finishedActivity!!
        assertEquals(240.seconds, done.elapsedTime)
        val gap = done.pauses.single()
        assertEquals(PauseKind.INTERRUPTION, gap.kind)
        assertEquals(120.0, gap.durationNanos / 1e9, 1e-9)
        assertEquals(59.0 * 3 + 59.0 * 3, done.distanceMeters, 4.0)
        assertEquals(118.0, done.movingTime.inWholeMilliseconds / 1000.0, 3.0)
        assertTrue(ActivityFlag.RECOVERED_AFTER_INTERRUPTION in done.flags)
        assertEquals(2, done.points.map { it.segment }.distinct().size)
    }

    @Test
    fun `reboot resets the monotonic clock and the wall clock sizes the gap`() {
        val before = Feed().action(Action.START).move(60, 3.0)
        val engine = engine(SportType.RUN)
        engine.processAll(before.inputs)
        val snapshot = engine.snapshot()
        // After a reboot, uptime restarts near zero; the wall clock says 90 s passed.
        val afterReboot = Feed(
            position = before.current(),
            baseNanos = 5_000_000_000L - (150.0 * 1e9).toLong(),
            startSeconds = 150.0,
        )
        val restored = RecordingEngine.restore(
            snapshot,
            Interruption(resumedAtMonotonicNanos = afterReboot.nanos(150.0), resumedAtWallClockMillis = 1_780_297_200_000L + 150_000L),
        )
        afterReboot.move(30, 3.0).action(Action.FINISH)
        restored.processAll(afterReboot.inputs)
        val done = restored.finishedActivity!!
        assertEquals(180.seconds, done.elapsedTime)
        assertEquals(90.0, done.pauses.single().durationNanos / 1e9, 1e-6)
        assertTrue(done.points.zipWithNext().all { (a, b) -> b.offsetNanos > a.offsetNanos })
        assertEquals(59.0 * 3 + 29.0 * 3, done.distanceMeters, 4.0)
    }

    @Test
    fun `interruption while manually paused keeps a single pause`() {
        val f = Feed().action(Action.START).move(30, 3.0).action(Action.PAUSE)
        val engine = engine(SportType.RUN)
        engine.processAll(f.inputs)
        val g = f.continueAt(f.current(), afterSeconds = 60.0)
        val restored = RecordingEngine.restore(engine.snapshot(), Interruption(g.nanos(g.seconds), null))
        assertEquals(RecordingStatus.PAUSED, restored.state.status)
        g.action(Action.RESUME).move(30, 3.0).action(Action.FINISH)
        restored.processAll(g.inputs)
        val done = restored.finishedActivity!!
        assertEquals(listOf(PauseKind.MANUAL), done.pauses.map { it.kind })
        assertEquals(60.0, done.pauses.single().durationNanos / 1e9, 1e-9)
        // Moving time comes from the track: the seconds before the first fix of each segment do not count.
        assertTrue(done.movingTime in 57.seconds..60.seconds, "moving ${done.movingTime}")
    }
}
