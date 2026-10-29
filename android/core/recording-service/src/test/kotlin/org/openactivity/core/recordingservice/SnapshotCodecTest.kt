// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.recordingservice.journal.SnapshotCodec
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.RecordingEngine
import java.io.IOException

class SnapshotCodecTest {
    @Test
    fun `snapshots round-trip exactly and the restored engine finishes identically`() {
        // Runs, rides, barometer, laps with a pause and heart rate, auto-pause, low-accuracy start.
        for (id in listOf("S001", "S006", "S023", "S028", "S029", "S005")) {
            val track = Corpus.load(id)
            val inputs = track.withTicks()
            val reference = RecordingEngine(track.config).apply { processAll(inputs) }.finishedActivity
            for (cut in listOf(inputs.size / 4, inputs.size / 2, inputs.size * 9 / 10)) {
                val engine = RecordingEngine(track.config).apply { processAll(inputs.subList(0, cut)) }
                val snapshot = engine.snapshot()
                val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))
                assertEquals("$id cut $cut", snapshot, decoded)
                val restored = RecordingEngine.restore(decoded)
                restored.processAll(inputs.subList(cut, inputs.size))
                assertEquals("$id cut $cut", reference, restored.finishedActivity)
            }
        }
    }

    @Test
    fun `an idle engine snapshot round-trips`() {
        val engine = RecordingEngine(Corpus.load("S001").config)
        assertEquals(engine.snapshot(), SnapshotCodec.decode(SnapshotCodec.encode(engine.snapshot())))
    }

    @Test
    fun `finished snapshots are never journalled`() {
        val engine = RecordingEngine(Corpus.load("S001").config)
        engine.process(EngineInput.UserAction(1, Action.START, 0))
        engine.process(EngineInput.UserAction(2, Action.FINISH))
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.encode(engine.snapshot()) }
    }

    @Test
    fun `garbage and truncated input are rejected with IOException`() {
        assertThrows(IOException::class.java) { SnapshotCodec.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        val bytes = SnapshotCodec.encode(RecordingEngine(Corpus.load("S001").config).snapshot())
        val error = runCatching { SnapshotCodec.decode(bytes.copyOf(bytes.size / 2)) }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
