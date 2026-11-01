// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording

import org.junit.jupiter.api.Test
import org.openactivity.model.LapTrigger
import org.openactivity.model.SportType
import kotlin.test.assertEquals

class AutoLapTest {

    private fun run(autoLapMeters: Double, build: Feed.() -> Unit): FinishedActivity {
        val feed = Feed().apply { action(Action.START) }
        feed.build()
        feed.action(Action.FINISH)
        val engine = RecordingEngine(RecordingConfig(SportType.RUN, autoPauseEnabled = false, autoLapMeters = autoLapMeters))
        engine.processAll(feed.inputs)
        return engine.finishedActivity!!
    }

    @Test
    fun `every full kilometre closes a lap of exactly 1000 m at the crossing time`() {
        // 2.4 km at 3 m/s: two auto laps and a 400 m session-end lap.
        val done = run(1000.0) { move(800, 3.0) }
        assertEquals(listOf(LapTrigger.DISTANCE, LapTrigger.DISTANCE, LapTrigger.SESSION_END), done.laps.map { it.trigger })
        assertEquals(1000.0, done.laps[0].distance.meters, 1e-6)
        assertEquals(1000.0, done.laps[1].distance.meters, 1e-6)
        assertEquals(done.distanceMeters - 2000.0, done.laps[2].distance.meters, 1e-6)
        // 1000 m at 3 m/s is 333 s, give or take the first fix that only anchors the track.
        assertEquals(334.0, done.laps[0].elapsedTime.inWholeMilliseconds / 1000.0, 1.5)
        assertEquals(333.3, done.laps[1].elapsedTime.inWholeMilliseconds / 1000.0, 1.0)
        assertEquals(done.laps[0].elapsedTime, done.laps[1].startOffset)
    }

    @Test
    fun `a manual lap restarts the auto-lap count`() {
        val done = run(1000.0) {
            move(200, 3.0) // 600 m
            action(Action.LAP)
            move(400, 3.0) // 1200 m more: one auto lap 1000 m after the manual one
        }
        assertEquals(listOf(LapTrigger.MANUAL, LapTrigger.DISTANCE, LapTrigger.SESSION_END), done.laps.map { it.trigger })
        assertEquals(1000.0, done.laps[1].distance.meters, 1e-6)
    }

    @Test
    fun `no auto laps while paused or when switched off`() {
        val paused = run(500.0) {
            move(100, 3.0) // 300 m
            action(Action.PAUSE)
            move(200, 3.0) // not counted
            action(Action.RESUME)
            move(10, 3.0)
        }
        assertEquals(listOf(LapTrigger.SESSION_END), paused.laps.map { it.trigger })
        val off = run(0.0) { move(800, 3.0) }
        assertEquals(1, off.laps.size)
    }

    @Test
    fun `auto-lap survives a snapshot and restore`() {
        val feed = Feed().apply { action(Action.START) }.move(250, 3.0)
        val config = RecordingConfig(SportType.RUN, autoPauseEnabled = false, autoLapMeters = 1000.0)
        val first = RecordingEngine(config).apply { processAll(feed.inputs) }
        val restored = RecordingEngine.restore(first.snapshot())
        assertEquals(1000.0, restored.config.autoLapMeters)
        val more = feed.continueAt(feed.current()).also { it.inputs.clear() }.move(200, 3.0).action(Action.FINISH)
        restored.processAll(more.inputs)
        val laps = restored.finishedActivity!!.laps
        assertEquals(LapTrigger.DISTANCE, laps.first().trigger)
        assertEquals(1000.0, laps.first().distance.meters, 1e-6)
    }
}
