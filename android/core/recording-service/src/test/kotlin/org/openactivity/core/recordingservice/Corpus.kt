// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.RecordingConfig
import java.io.File

/** A corpus track (testdata/gps-corpus) as engine inputs. */
internal data class CorpusTrack(
    val id: String,
    val sport: SportType,
    val autoPause: Boolean,
    val units: UnitSystem,
    val inputs: List<EngineInput>,
) {
    val config: RecordingConfig get() = RecordingConfig(sport, autoPauseEnabled = autoPause, unitSystem = units)

    val fixes: List<EngineInput.Location> get() = inputs.filterIsInstance<EngineInput.Location>()

    /** Inputs with a one-second tick between fixes, as the service feeds them. */
    fun withTicks(): List<EngineInput> {
        val out = ArrayList<EngineInput>(inputs.size * 2)
        var lastTick = inputs.first().monotonicNanos
        for (input in inputs) {
            while (input.monotonicNanos - lastTick >= 1_000_000_000L) {
                lastTick += 1_000_000_000L
                out += EngineInput.Tick(lastTick)
            }
            out += input
        }
        return out
    }
}

/** Reads the `.sfraw.jsonl` corpus files (flat JSON objects, one per line). */
internal object Corpus {
    private val dir = File(System.getProperty("gpsCorpusDir") ?: "../../../testdata/gps-corpus")

    fun load(id: String): CorpusTrack {
        val file = File(dir, "synthetic").listFiles().orEmpty().single { it.name.startsWith("$id-") }
        var sport = SportType.RUN
        var autoPause = false
        var units = UnitSystem.METRIC
        val inputs = mutableListOf<EngineInput>()
        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val f = fields(line)
            val t = f["t"]?.toLong()
            when (f["k"]) {
                "meta" -> {
                    sport = SportType.fromApiName(f.getValue("sport")) ?: SportType.OTHER
                    autoPause = f["autoPause"] == "true"
                    units = if (f["units"] == "imperial") UnitSystem.IMPERIAL else UnitSystem.METRIC
                }
                "act" -> inputs += EngineInput.UserAction(t!!, Action.valueOf(f.getValue("a")), f["wall"]?.toLong())
                "loc" -> inputs += EngineInput.Location(
                    monotonicNanos = t!!,
                    wallClockMillis = f.getValue("wall").toLong(),
                    latitude = f.getValue("lat").toDouble(),
                    longitude = f.getValue("lon").toDouble(),
                    horizontalAccuracyMeters = f["acc"]?.toDouble(),
                    altitudeMeters = f["alt"]?.toDouble(),
                    verticalAccuracyMeters = f["vacc"]?.toDouble(),
                    speedMps = f["spd"]?.toDouble(),
                    bearingDegrees = f["brg"]?.toDouble(),
                    isMock = f["mock"] == "true",
                )
                "baro" -> inputs += EngineInput.Pressure(t!!, f.getValue("hpa").toDouble())
                "hr" -> inputs += EngineInput.HeartRate(t!!, f.getValue("bpm").toDouble().toInt())
            }
        }
        return CorpusTrack(id, sport, autoPause, units, inputs)
    }

    private val field = Regex("\"(\\w+)\":(\"[^\"]*\"|[^,}]+)")

    private fun fields(line: String): Map<String, String> =
        field.findAll(line).associate { m -> m.groupValues[1] to m.groupValues[2].trim('"') }
}
