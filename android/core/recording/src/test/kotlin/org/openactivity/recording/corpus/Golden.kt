// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import org.openactivity.recording.Action
import org.openactivity.recording.ActivityFlag

/**
 * Builds a scenario's golden `*.expected.json` from the generator's **ground truth** (never from engine
 * output). Values describe what a perfect recorder would report for the observable part of the track:
 * distance starts at the first fix the engine can accept and ends at the last fix before FINISH.
 * Tolerances follow docs/99-gaps/gps-qa-test-performance.md §3.3.
 */
internal object Golden {
    const val ENGINE_VERSION = ">=0.1.0"

    /** Best-effort time tolerances by target distance (policy §3.3). */
    private fun bestEffortTolerance(targetM: Double): Double = when {
        targetM <= 1_000.0 -> 2.0
        targetM <= 5_000.0 -> 3.0
        targetM <= 10_000.0 -> 5.0
        targetM <= 21_097.5 -> 8.0
        else -> 15.0
    }

    fun expectedJson(g: Generated): String {
        val sc = g.scenario
        val truth = g.truth
        val m = truth.motion
        val presses = truth.actions.filter { it.action == Action.LAP }.map { it.atS }
        val manualPauses = pauseWindows(truth.actions)
        val timerMode = sc.autoPause || manualPauses.isNotEmpty()
        val distStart = m.distanceAt(truth.firstGoodFixS)
        val distEnd = m.distanceAt(truth.lastFixS)
        val distance = distEnd - distStart
        val movingFrom = if (timerMode) 0.0 else truth.firstGoodFixS
        val movingTo = if (timerMode) truth.finishS else truth.lastFixS
        val moving = m.movingSecondsBetween(movingFrom, movingTo) -
            manualPauses.sumOf { m.movingSecondsBetween(it.fromS, it.toS) }
        val elapsed = (TrackGen.mono(truth.finishS) - TrackGen.mono(0.0)) / 1e9
        val tol = sc.tolerances

        val fields = ArrayList<String>()
        fields += num("distance_m", distance, "\"tol_pct\": ${f(tol.distancePct)}" +
            if (tol.distancePct == 0.0) ", \"tol_abs\": 10.000" else "")
        fields += num("moving_time_s", moving, "\"tol_abs\": ${f(maxOf(5.0, moving * tol.movingPct / 100))}")
        fields += num("elapsed_time_s", elapsed, "\"tol_abs\": 0.001")
        elevationTolerance(sc)?.let { et ->
            val (gain, loss) = TrackGen.elevationTruth(sc, distStart, distEnd)
            val spec = "\"tol_pct_over\": ${f(et.pctOver)}, \"tol_pct_under\": ${f(et.pctUnder)}, \"tol_abs\": ${f(et.absM)}"
            fields += num("elevation_gain_m", gain, spec)
            fields += num("elevation_loss_m", loss, spec)
        }
        if (sc.checkMaxSpeed) fields += num("max_speed_mps", m.maxSpeed, "\"tol_pct\": 5.000")
        if (sc.autoPause) {
            fields += "\"auto_pause_count\": {\"value\": ${m.stopCount}, \"tol_abs\": ${if (m.stopCount > 1) 1 else 0}}"
        }
        if (sc.bestEffortTargets.isNotEmpty()) {
            require(sc.phases.size == 1 && sc.phases[0] is Phase.Move) { "${sc.id}: best-effort truth needs constant speed" }
            val speed = (sc.phases[0] as Phase.Move).speedMps
            val entries = sc.bestEffortTargets.joinToString(",\n") { (name, target) ->
                "      \"$name\": {\"target_m\": ${f(target)}, \"elapsed_s\": ${f(target / speed)}, " +
                    "\"tol_abs\": ${f(bestEffortTolerance(target))}}"
            }
            fields += "\"best_efforts\": {\n$entries\n    }"
        }
        fields += splitsJson(g, distStart, distEnd)
        if (presses.isNotEmpty()) fields += lapsJson(g, presses, distStart)
        fields += "\"start_epoch_ms\": {\"value\": ${sc.startEpochMillis}, \"tol_abs\": 0}"
        if (sc.checkAntimeridian) fields += "\"bbox_crosses_antimeridian\": true"
        fields += "\"flags\": [${flags(g).joinToString(", ") { "\"$it\"" }}]"

        return buildString {
            append("{\n")
            append("  \"corpus_id\": \"${sc.id}\",\n")
            append("  \"engine_version\": \"$ENGINE_VERSION\",\n")
            append("  \"expected\": {\n    ")
            append(fields.joinToString(",\n    "))
            append("\n  },\n")
            append("  \"rationale\": \"Analytic truth from TrackGen seed ${sc.seed} (observable window: first fix ")
            append("with accuracy <= ${TrackGen.MAX_ACCURACY_M.toInt()} m to last fix before FINISH); tolerances per ")
            append("gps-qa-test-performance.md section 3.3.\"\n")
            append("}\n")
        }
    }

    /**
     * The scenario's elevation tolerance; flat GPS-only scenarios default to the policy's flat-ground cap
     * (gain and loss at most 30 m when the truth is 0, gps-qa §2.3 S025).
     */
    private fun elevationTolerance(sc: Scenario): ElevationTolerance? {
        sc.tolerances.elevation?.let { return it }
        val flat = sc.elevation.all { it.altitudeM == sc.elevation.first().altitudeM }
        return if (flat && sc.barometer == null) ElevationTolerance(pctOver = 0.0, pctUnder = 0.0, absM = 30.0) else null
    }

    private fun splitsJson(g: Generated, distStart: Double, distEnd: Double): String {
        val m = g.truth.motion
        val times = ArrayList<Double>()
        var previous = g.truth.firstGoodFixS
        var k = 1
        while (distStart + k * 1000.0 <= distEnd) {
            val t = m.timeAtDistance(distStart + k * 1000.0)
            times += t - previous
            previous = t
            k++
        }
        return "\"splits_km\": {\"count\": ${times.size}, \"elapsed_s\": [${times.joinToString(", ") { f(it) }}], " +
            "\"each_tol_abs_s\": ${f(g.scenario.tolerances.splitEachS)}}"
    }

    private fun lapsJson(g: Generated, presses: List<Double>, distStart: Double): String {
        val m = g.truth.motion
        val bounds = listOf(g.truth.firstGoodFixS) + presses + g.truth.lastFixS
        val distances = bounds.zipWithNext().mapIndexed { i, (a, b) ->
            m.distanceAt(b) - if (i == 0) distStart else m.distanceAt(a)
        }
        return "\"laps\": {\"count\": ${distances.size}, \"distance_m\": [${distances.joinToString(", ") { f(it) }}], " +
            "\"each_tol_pct\": ${f(g.scenario.tolerances.lapDistancePct)}, \"each_tol_abs_m\": 15.000}"
    }

    private fun flags(g: Generated): List<String> {
        val out = ArrayList<String>()
        val m = g.truth.motion
        val fixes = g.truth.fixTimesS
        val significant = g.scenario.gaps.any { gap ->
            val before = fixes.lastOrNull { it < gap.fromS } ?: return@any false
            val after = fixes.firstOrNull { it >= gap.toS } ?: return@any false
            after - before > 10.0 && m.distanceAt(after) - m.distanceAt(before) > 100.0
        }
        if (significant) out += ActivityFlag.SIGNIFICANT_GPS_GAP.name
        return out
    }

    private fun pauseWindows(actions: List<TimedAction>): List<Window> {
        val out = ArrayList<Window>()
        var start: Double? = null
        for (a in actions.sortedBy { it.atS }) when (a.action) {
            Action.PAUSE -> start = a.atS
            Action.RESUME, Action.FINISH -> start?.let { out += Window(it, a.atS); start = null }
            else -> Unit
        }
        return out
    }

    private fun num(name: String, value: Double, tolerance: String) = "\"$name\": {\"value\": ${f(value)}, $tolerance}"

    private fun f(v: Double) = SfRaw.f(v, 3)
}
