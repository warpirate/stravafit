// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.openactivity.model.SportType
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import java.util.Locale

/**
 * The `.sfraw.jsonl` corpus format: one JSON object per line, raw as recorded (all fixes, with accuracy,
 * which GPX would lose).
 *
 * ```
 * {"k":"meta","id":"S001","sport":"Run","autoPause":false,"units":"metric","seed":1001}
 * {"k":"act","t":<monotonic ns>,"a":"START","wall":<epoch ms>}
 * {"k":"loc","t":..,"wall":..,"lat":..,"lon":..,"acc":..,"alt":..,"vacc":..,"spd":..,"brg":..}
 * {"k":"baro","t":..,"hpa":..}
 * {"k":"hr","t":..,"bpm":..}
 * ```
 *
 * Ticks are not stored: the replay harness synthesises one per second, as the recording service does.
 */
internal object SfRaw {
    /** Recording settings stored in the file header. */
    data class Meta(val id: String, val sport: SportType, val autoPause: Boolean, val seed: Long)

    /** A parsed corpus file. */
    class Track(val meta: Meta, val inputs: List<EngineInput>)

    fun write(sc: Scenario, events: List<RawEvent>): String = buildString {
        append("{\"k\":\"meta\",\"id\":\"${sc.id}\",\"sport\":\"${sc.sport.apiName}\",")
        append("\"autoPause\":${sc.autoPause},\"units\":\"metric\",\"seed\":${sc.seed}}\n")
        for (e in events) {
            when (e) {
                is RawEvent.Act -> append("{\"k\":\"act\",\"t\":${e.t},\"a\":\"${e.action.name}\",\"wall\":${e.wall}}")
                is RawEvent.Loc -> append(
                    "{\"k\":\"loc\",\"t\":${e.t},\"wall\":${e.wall},\"lat\":${f(e.lat, 8)},\"lon\":${f(e.lon, 8)}," +
                        "\"acc\":${f(e.acc, 2)},\"alt\":${f(e.alt, 2)},\"vacc\":${f(e.vacc, 2)}," +
                        "\"spd\":${f(e.spd, 3)},\"brg\":${f(e.brg, 1)}}",
                )
                is RawEvent.Baro -> append("{\"k\":\"baro\",\"t\":${e.t},\"hpa\":${f(e.hpa, 4)}}")
                is RawEvent.Hr -> append("{\"k\":\"hr\",\"t\":${e.t},\"bpm\":${e.bpm}}")
            }
            append('\n')
        }
    }

    fun read(text: String): Track {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val head = Json.parseToJsonElement(lines.first()).jsonObject
        require(head.str("k") == "meta") { "first line must be the meta header" }
        val meta = Meta(
            id = head.str("id"),
            sport = requireNotNull(SportType.fromApiName(head.str("sport"))) { "unknown sport" },
            autoPause = head.getValue("autoPause").jsonPrimitive.boolean,
            seed = head.getValue("seed").jsonPrimitive.long,
        )
        val inputs = lines.drop(1).map { line -> toInput(Json.parseToJsonElement(line).jsonObject) }
        return Track(meta, inputs)
    }

    private fun toInput(o: JsonObject): EngineInput {
        val t = o.getValue("t").jsonPrimitive.long
        return when (val k = o.str("k")) {
            "loc" -> EngineInput.Location(
                monotonicNanos = t,
                wallClockMillis = o.getValue("wall").jsonPrimitive.long,
                latitude = o.d("lat"),
                longitude = o.d("lon"),
                horizontalAccuracyMeters = o.d("acc"),
                altitudeMeters = o.d("alt"),
                verticalAccuracyMeters = o.d("vacc"),
                speedMps = o.d("spd"),
                bearingDegrees = o.d("brg"),
            )
            "act" -> EngineInput.UserAction(t, Action.valueOf(o.str("a")), o.getValue("wall").jsonPrimitive.long)
            "baro" -> EngineInput.Pressure(t, o.d("hpa"))
            "hr" -> EngineInput.HeartRate(t, o.getValue("bpm").jsonPrimitive.int)
            else -> error("unknown record kind '$k'")
        }
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.d(key: String) = getValue(key).jsonPrimitive.double

    fun f(v: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", v)
}
