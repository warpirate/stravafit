// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.FinishedActivity
import org.openactivity.recording.RecordingConfig
import org.openactivity.recording.RecordingEngine
import java.io.File

/** A manifest entry. */
internal data class ManifestEntry(val id: String, val name: String, val track: String, val expected: String)

/** Loads the corpus and replays tracks through the production [RecordingEngine]. */
internal object Replay {
    const val TICK_NANOS: Long = 1_000_000_000L

    /** Corpus root, passed in by Gradle (falls back to the repository layout when run from the IDE). */
    val corpusRoot: File by lazy {
        System.getProperty("gpsCorpusDir")?.let(::File)
            ?: generateSequence(File("").absoluteFile) { it.parentFile }
                .map { it.resolve("testdata/gps-corpus") }
                .first { it.isDirectory }
    }

    /** Minimal reader for the generated manifest (flat `key: value` entries under `scenarios:`). */
    fun manifest(root: File = corpusRoot): List<ManifestEntry> {
        val entries = ArrayList<ManifestEntry>()
        var current = HashMap<String, String>()
        fun flush() {
            if (current.isNotEmpty()) {
                entries += ManifestEntry(
                    id = current.getValue("id"),
                    name = current.getValue("name"),
                    track = current.getValue("track"),
                    expected = current.getValue("expected"),
                )
            }
            current = HashMap()
        }
        for (raw in root.resolve("manifest.yaml").readLines()) {
            val line = raw.substringBefore(" #").trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val trimmed = line.trim()
            if (trimmed.startsWith("- ")) {
                flush()
                val (k, v) = trimmed.removePrefix("- ").split(":", limit = 2)
                current[k.trim()] = v.trim()
            } else if (line.startsWith("    ")) {
                val (k, v) = trimmed.split(":", limit = 2)
                current[k.trim()] = v.trim()
            }
        }
        flush()
        return entries
    }

    /** Engine inputs of [track] merged with one tick per second from START to FINISH. */
    fun inputsWithTicks(track: SfRaw.Track): List<EngineInput> {
        val actions = track.inputs.filterIsInstance<EngineInput.UserAction>()
        val start = actions.first { it.action == Action.START }.monotonicNanos
        val finish = actions.last { it.action == Action.FINISH }.monotonicNanos
        val ticks = generateSequence(start + TICK_NANOS) { it + TICK_NANOS }
            .takeWhile { it < finish }
            .map { EngineInput.Tick(it) }
            .toList()
        // Stable sort keeps recorded inputs before a tick with the same timestamp.
        return (track.inputs + ticks).sortedBy { it.monotonicNanos }
    }

    fun configFor(track: SfRaw.Track): RecordingConfig =
        RecordingConfig(sport = track.meta.sport, autoPauseEnabled = track.meta.autoPause)

    /** Replays [track] and returns the finished activity. */
    fun run(track: SfRaw.Track): FinishedActivity {
        val engine = RecordingEngine(configFor(track))
        engine.processAll(inputsWithTicks(track))
        return requireNotNull(engine.finishedActivity) { "${track.meta.id}: engine did not finish" }
    }

    fun loadTrack(entry: ManifestEntry, root: File = corpusRoot): SfRaw.Track =
        SfRaw.read(root.resolve(entry.track).readText())
}
