// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.openactivity.geo.BoundingBox
import org.openactivity.model.LatLng
import org.openactivity.recording.FinishedActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden replay: every corpus track goes through the production engine, and the result must match the
 * generator's ground truth within the golden file's tolerances.
 */
class GoldenReplayTest {
    @Test
    fun `corpus has at least twelve scenarios and every file exists`() {
        val entries = Replay.manifest()
        assertTrue(entries.size >= 12, "only ${entries.size} scenarios")
        assertEquals(entries.size, entries.map { it.id }.toSet().size, "duplicate ids")
        entries.forEach { e ->
            assertTrue(Replay.corpusRoot.resolve(e.track).isFile, "missing ${e.track}")
            assertTrue(Replay.corpusRoot.resolve(e.expected).isFile, "missing ${e.expected}")
        }
    }

    @TestFactory
    fun `replay matches golden`(): List<DynamicTest> = Replay.manifest().map { entry ->
        DynamicTest.dynamicTest("${entry.id} ${entry.name}") {
            val track = Replay.loadTrack(entry)
            val result = Replay.run(track)
            val golden = Json.parseToJsonElement(Replay.corpusRoot.resolve(entry.expected).readText()).jsonObject
            assertEquals(entry.id, golden.getValue("corpus_id").jsonPrimitive.content)
            val failures = GoldenCheck(result, golden.getValue("expected").jsonObject).run()
            if (failures.isNotEmpty()) fail("${entry.id} ${entry.name}:\n  " + failures.joinToString("\n  "))
        }
    }

    @TestFactory
    fun `replay is deterministic`(): List<DynamicTest> = Replay.manifest().map { entry ->
        DynamicTest.dynamicTest(entry.id) {
            val track = Replay.loadTrack(entry)
            assertEquals(Replay.run(track), Replay.run(track))
        }
    }
}

/** Compares a finished activity with a golden `expected` object and lists every violation. */
internal class GoldenCheck(private val a: FinishedActivity, private val expected: JsonObject) {
    private val failures = ArrayList<String>()

    fun run(): List<String> {
        invariants()
        metric("distance_m", a.distanceMeters)
        metric("moving_time_s", a.movingTime.inWholeNanoseconds / 1e9)
        metric("elapsed_time_s", a.elapsedTime.inWholeNanoseconds / 1e9)
        metric("elevation_gain_m", a.elevationGainMeters)
        metric("elevation_loss_m", a.elevationLossMeters)
        metric("max_speed_mps", a.maxSpeedMps)
        metric("auto_pause_count", a.autoPauseCount.toDouble())
        bestEfforts()
        splits()
        laps()
        expected["start_epoch_ms"]?.jsonObject?.let { spec ->
            val want = spec.getValue("value").jsonPrimitive.long
            if (a.startEpochMillis != want) failures += "start_epoch_ms: expected $want, got ${a.startEpochMillis}"
        }
        expected["bbox_crosses_antimeridian"]?.let { want ->
            val box = BoundingBox.of(a.points.map { LatLng(it.latitude, it.longitude) })
            if (box?.crossesAntimeridian != want.jsonPrimitive.boolean) {
                failures += "bbox_crosses_antimeridian: expected $want, got $box"
            }
        }
        val wantFlags = expected.getValue("flags").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val gotFlags = a.flags.map { it.name }.toSet()
        if (wantFlags != gotFlags) failures += "flags: expected $wantFlags, got $gotFlags"
        return failures
    }

    private fun invariants() {
        if (a.movingTime > a.elapsedTime) failures += "moving time ${a.movingTime} exceeds elapsed ${a.elapsedTime}"
        listOf(a.distanceMeters, a.elevationGainMeters, a.elevationLossMeters, a.averageSpeedMps, a.maxSpeedMps)
            .forEach { if (!it.isFinite()) failures += "non-finite metric $it" }
        a.points.zipWithNext().forEachIndexed { i, (p, q) ->
            if (q.offsetNanos <= p.offsetNanos) failures += "point ${i + 1}: time not increasing"
            if (q.epochMillis < p.epochMillis) failures += "point ${i + 1}: wall time goes backwards"
            if (q.distanceMeters < p.distanceMeters) failures += "point ${i + 1}: distance decreases"
        }
    }

    private fun metric(name: String, actual: Double) {
        val spec = expected[name]?.jsonObject ?: return
        val want = spec.getValue("value").jsonPrimitive.double
        val abs = spec.num("tol_abs") ?: 0.0
        val pct = spec.num("tol_pct") ?: 0.0
        val over = max(abs, abs(want) * (spec.num("tol_pct_over") ?: pct) / 100)
        val under = max(abs, abs(want) * (spec.num("tol_pct_under") ?: pct) / 100)
        if (actual > want + over + 1e-9 || actual < want - under - 1e-9) {
            failures += "$name: expected ${"%.3f".format(want)} (-${"%.3f".format(under)}/+${"%.3f".format(over)}), " +
                "got ${"%.3f".format(actual)} (diff ${"%+.3f".format(actual - want)})"
        }
    }

    private fun bestEfforts() {
        val spec = expected["best_efforts"]?.jsonObject ?: return
        for ((name, e) in spec) {
            val o = e.jsonObject
            val target = o.getValue("target_m").jsonPrimitive.double
            val want = o.getValue("elapsed_s").jsonPrimitive.double
            val tol = o.getValue("tol_abs").jsonPrimitive.double
            val got = a.bestEfforts.firstOrNull { abs(it.targetMeters - target) < 1e-6 }
            if (got == null) {
                failures += "best effort $name: missing"
            } else if (abs(got.elapsedSeconds - want) > tol + 1e-9) {
                failures += "best effort $name: expected ${"%.2f".format(want)} ±$tol s, got ${"%.2f".format(got.elapsedSeconds)}"
            }
        }
    }

    private fun splits() {
        val spec = expected["splits_km"]?.jsonObject ?: return
        val full = a.splits.filterNot { it.isPartial }
        val count = spec.getValue("count").jsonPrimitive.int
        if (full.size != count) {
            failures += "splits: expected $count full splits, got ${full.size}"
            return
        }
        val tol = spec.getValue("each_tol_abs_s").jsonPrimitive.double
        spec.getValue("elapsed_s").jsonArray.map { it.jsonPrimitive.double }.forEachIndexed { i, want ->
            val got = full[i].elapsedTime.inWholeNanoseconds / 1e9
            if (abs(got - want) > tol + 1e-9) failures += "split ${i + 1}: expected ${"%.2f".format(want)} ±$tol s, got ${"%.2f".format(got)}"
        }
    }

    private fun laps() {
        val spec = expected["laps"]?.jsonObject ?: return
        val count = spec.getValue("count").jsonPrimitive.int
        if (a.laps.size != count) {
            failures += "laps: expected $count, got ${a.laps.size}"
            return
        }
        val pct = spec.getValue("each_tol_pct").jsonPrimitive.double
        val absTol = spec.getValue("each_tol_abs_m").jsonPrimitive.double
        spec.getValue("distance_m").jsonArray.map { it.jsonPrimitive.double }.forEachIndexed { i, want ->
            val got = a.laps[i].distance.meters
            val tol = max(absTol, want * pct / 100)
            if (abs(got - want) > tol) failures += "lap ${i + 1}: expected ${"%.1f".format(want)} ±${"%.1f".format(tol)} m, got ${"%.1f".format(got)}"
        }
    }

    private fun JsonObject.num(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
}
