// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import org.openactivity.model.SportType
import org.openactivity.recording.Action
import kotlin.random.Random

/** One piece of the true path. */
internal sealed interface Leg {
    /** Straight ahead on [bearingDeg] for [lengthM]. */
    data class Straight(val bearingDeg: Double, val lengthM: Double) : Leg

    /** A circular turn of [angleDeg] (positive = right/clockwise) with [radiusM]. */
    data class Turn(val angleDeg: Double, val radiusM: Double) : Leg
}

/** One piece of the true speed profile. Speed changes are instantaneous, which keeps the truth analytic. */
internal sealed interface Phase {
    data class Move(val distanceM: Double, val speedMps: Double) : Phase
    data class Stop(val seconds: Double) : Phase
}

/** True altitude [altitudeM] at along-track distance [distanceM]; linear in between. */
internal data class Knot(val distanceM: Double, val altitudeM: Double)

/**
 * Horizontal error: a first-order Gauss–Markov process (correlated over [tauS]) with standard deviation
 * [sigmaM] per axis, plus optional white noise. Vertical error uses the same model scaled by [verticalFactor].
 */
internal data class Noise(
    val sigmaM: Double,
    val tauS: Double = 60.0,
    val whiteM: Double = 0.0,
    val verticalFactor: Double = 1.5,
)

/** GPS warm-up: sigma starts at [startSigmaM] and decays towards the base sigma over [seconds]. */
internal data class Warmup(val seconds: Double, val startSigmaM: Double)

/** Multipath jumps of [jumpM] for single samples, roughly every [meanIntervalS] seconds. */
internal data class Multipath(val meanIntervalS: Double, val jumpM: Double)

/** A span of activity time, in seconds since START. */
internal data class Window(val fromS: Double, val toS: Double) {
    operator fun contains(t: Double): Boolean = t >= fromS && t < toS
}

/** The device wall clock jumps by [deltaMillis] at [atS] (DST, NTP correction). */
internal data class WallJump(val atS: Double, val deltaMillis: Long)

/** A barometer with white noise and a linear weather drift. */
internal data class Barometer(val noiseHpa: Double, val driftHpaPerHour: Double)

/** A user command at [atS] seconds after START. */
internal data class TimedAction(val atS: Double, val action: Action)

/** Tolerance overrides for a scenario's golden file (policy defaults otherwise). */
internal data class Tolerances(
    val distancePct: Double,
    val splitEachS: Double = 3.0,
    val elevation: ElevationTolerance? = null,
    val movingPct: Double = 1.0,
    val lapDistancePct: Double = 2.5,
)

/** Elevation gain/loss tolerance: percentage over/under truth with an absolute floor. */
internal data class ElevationTolerance(val pctOver: Double, val pctUnder: Double, val absM: Double)

/**
 * A synthetic scenario: the truth (path, speed, altitude) plus the ways the measurement is corrupted.
 * [TrackGen.generate] turns it into recorder events and the analytic ground truth.
 */
internal data class Scenario(
    val id: String,
    val name: String,
    val description: String,
    val sport: SportType,
    val seed: Long,
    val tags: List<String>,
    val startLat: Double,
    val startLon: Double,
    val startEpochMillis: Long,
    val legs: List<Leg>,
    val phases: List<Phase>,
    val elevation: List<Knot>,
    val noise: Noise,
    val autoPause: Boolean,
    val tolerances: Tolerances,
    val warmup: Warmup? = null,
    val multipath: Multipath? = null,
    val gaps: List<Window> = emptyList(),
    val wallJumps: List<WallJump> = emptyList(),
    val barometer: Barometer? = null,
    val heartRate: Boolean = false,
    /** Extra actions; START at 0 s and FINISH at the end are added automatically. */
    val actions: (MotionProfile) -> List<TimedAction> = { emptyList() },
    val bestEffortTargets: List<Pair<String, Double>> = emptyList(),
    val checkMaxSpeed: Boolean = false,
    val checkAntimeridian: Boolean = false,
)

/** The true path as a dense polyline (≤ 5 m between vertices) with cumulative distance. */
internal class TruePath(startLat: Double, startLon: Double, legs: List<Leg>) {
    private val lats = ArrayList<Double>()
    private val lons = ArrayList<Double>()
    private val cum = ArrayList<Double>()

    init {
        lats += startLat
        lons += startLon
        cum += 0.0
        var heading = (legs.firstOrNull() as? Leg.Straight)?.bearingDeg ?: 0.0
        for (leg in legs) when (leg) {
            is Leg.Straight -> {
                heading = leg.bearingDeg
                val steps = StrictMath.ceil(leg.lengthM / 5.0).toInt().coerceAtLeast(1)
                repeat(steps) { add(heading, leg.lengthM / steps) }
            }
            is Leg.Turn -> {
                val arc = StrictMath.abs(leg.angleDeg) * StrictMath.PI / 180.0 * leg.radiusM
                val steps = StrictMath.ceil(arc / 1.0).toInt().coerceAtLeast(1)
                val dTheta = leg.angleDeg / steps
                val chord = 2 * leg.radiusM * StrictMath.sin(StrictMath.abs(dTheta) * StrictMath.PI / 360.0)
                repeat(steps) {
                    add(GenMath.normalizeBearing(heading + dTheta / 2), chord)
                    heading = GenMath.normalizeBearing(heading + dTheta)
                }
            }
        }
    }

    private fun add(bearing: Double, length: Double) {
        val i = lats.size - 1
        val p = GenMath.destination(lats[i], lons[i], bearing, length)
        val d = GenMath.distance(lats[i], lons[i], p[0], p[1])
        lats += p[0]
        lons += p[1]
        cum += cum[i] + d
    }

    val length: Double get() = cum.last()

    /** Position and direction of travel at along-track distance [s]. */
    fun at(s: Double): Triple<Double, Double, Double> {
        require(s <= length + 1e-6) { "path too short: need $s m, have $length m" }
        var lo = 0
        var hi = cum.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cum[mid] <= s) lo = mid else hi = mid - 1
        }
        val i = lo.coerceAtMost(cum.size - 2)
        val span = cum[i + 1] - cum[i]
        val f = if (span > 0) ((s - cum[i]) / span).coerceIn(0.0, 1.0) else 0.0
        val dLon = GenMath.normalizeLon(lons[i + 1] - lons[i])
        val lat = lats[i] + (lats[i + 1] - lats[i]) * f
        val lon = GenMath.normalizeLon(lons[i] + dLon * f)
        return Triple(lat, lon, GenMath.bearing(lats[i], lons[i], lats[i + 1], lons[i + 1]))
    }
}

/** Piecewise-constant speed profile: distance and speed as functions of time. */
internal class MotionProfile(phases: List<Phase>) {
    private class Seg(val t0: Double, val duration: Double, val s0: Double, val speed: Double)

    private val segs = ArrayList<Seg>()
    val duration: Double
    val distance: Double

    init {
        var t = 0.0
        var s = 0.0
        for (p in phases) when (p) {
            is Phase.Move -> {
                val d = p.distanceM / p.speedMps
                segs += Seg(t, d, s, p.speedMps)
                t += d
                s += p.distanceM
            }
            is Phase.Stop -> {
                segs += Seg(t, p.seconds, s, 0.0)
                t += p.seconds
            }
        }
        duration = t
        distance = s
    }

    private fun seg(t: Double): Seg {
        for (g in segs) if (t < g.t0 + g.duration) return g
        return segs.last()
    }

    fun distanceAt(t: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= duration) return distance
        val g = seg(t)
        return g.s0 + g.speed * (t - g.t0)
    }

    fun speedAt(t: Double): Double = if (t < 0.0 || t >= duration) 0.0 else seg(t).speed

    /** First time at which the distance reaches [s]. */
    fun timeAtDistance(s: Double): Double {
        for (g in segs) if (g.speed > 0 && s <= g.s0 + g.speed * g.duration + 1e-9) {
            return g.t0 + ((s - g.s0) / g.speed).coerceAtLeast(0.0)
        }
        return duration
    }

    /** Seconds with speed > 0 inside [from, to]. */
    fun movingSecondsBetween(from: Double, to: Double): Double {
        var total = 0.0
        for (g in segs) if (g.speed > 0) {
            val a = maxOf(from, g.t0)
            val b = minOf(to, g.t0 + g.duration)
            if (b > a) total += b - a
        }
        return total
    }

    /** Number of stop phases. */
    val stopCount: Int get() = segs.count { it.speed == 0.0 }

    /** Highest speed. */
    val maxSpeed: Double get() = segs.maxOf { it.speed }

    /** Start and end time of every stop phase. */
    val stops: List<Window> get() = segs.filter { it.speed == 0.0 }.map { Window(it.t0, it.t0 + it.duration) }
}

/** One recorder event in corpus form (monotonic nanos plus payload). */
internal sealed interface RawEvent {
    val t: Long

    data class Loc(
        override val t: Long,
        val wall: Long,
        val lat: Double,
        val lon: Double,
        val acc: Double,
        val alt: Double,
        val vacc: Double,
        val spd: Double,
        val brg: Double,
    ) : RawEvent

    data class Baro(override val t: Long, val hpa: Double) : RawEvent
    data class Hr(override val t: Long, val bpm: Int) : RawEvent
    data class Act(override val t: Long, val action: Action, val wall: Long) : RawEvent
}

/** Analytic ground truth of a generated scenario. */
internal data class Truth(
    val firstGoodFixS: Double,
    val lastFixS: Double,
    val finishS: Double,
    val path: TruePath,
    val motion: MotionProfile,
    val actions: List<TimedAction>,
    val fixTimesS: List<Double>,
)

/** Generated corpus entry: events plus truth. */
internal class Generated(val scenario: Scenario, val events: List<RawEvent>, val truth: Truth)

/**
 * Deterministic synthetic track generator.
 *
 * It builds the ground truth first (path, speed profile, altitude) and then corrupts it with the
 * scenario's noise and faults. Everything random comes from one seeded [Random], and all maths uses
 * [StrictMath], so the same scenario always yields byte-identical files.
 */
internal object TrackGen {
    /** Monotonic clock value at START (an arbitrary uptime, so nothing assumes zero). */
    const val START_MONO_NANOS: Long = 86_400_000_000_000L

    /** Accuracy limit the generator assumes for "first good fix" (the engine default). */
    const val MAX_ACCURACY_M: Double = 50.0

    fun generate(sc: Scenario): Generated {
        val rnd = Random(sc.seed)
        val gauss = Gaussian(rnd)
        val motion = MotionProfile(sc.phases)
        val path = TruePath(sc.startLat, sc.startLon, sc.legs)
        require(path.length + 1e-6 >= motion.distance) { "${sc.id}: path ${path.length} < motion ${motion.distance}" }
        val finishS = motion.duration
        val actions = (listOf(TimedAction(0.0, Action.START)) + sc.actions(motion) + TimedAction(finishS, Action.FINISH))
            .sortedBy { it.atS }
        val events = ArrayList<RawEvent>()
        for (a in actions) events += RawEvent.Act(mono(a.atS), a.action, wall(sc, a.atS))

        val unitE = GaussMarkov(sc.noise.tauS)
        val unitN = GaussMarkov(sc.noise.tauS)
        val unitV = GaussMarkov(sc.noise.tauS)
        var nextJump = sc.multipath?.let { it.meanIntervalS * (0.5 + rnd.nextDouble()) } ?: Double.POSITIVE_INFINITY
        var lastT = 0.0
        var firstGood = Double.NaN
        var lastFix = Double.NaN
        val fixTimes = ArrayList<Double>()
        var k = 0
        while (true) {
            val t = k * 1.0 + 0.2 + (rnd.nextDouble() - 0.5) * 0.3
            k++
            if (t >= finishS) break
            if (sc.gaps.any { t in it }) continue
            val dt = t - lastT
            lastT = t
            val ue = unitE.step(dt, gauss)
            val un = unitN.step(dt, gauss)
            val uv = unitV.step(dt, gauss)
            val sigma = sigmaAt(sc, t)
            val s = motion.distanceAt(t)
            val v = motion.speedAt(t)
            val (lat, lon, heading) = path.at(s)
            var east = sigma * ue + sc.noise.whiteM * gauss.next()
            var north = sigma * un + sc.noise.whiteM * gauss.next()
            val mp = sc.multipath
            if (mp != null && t >= nextJump) {
                val dir = rnd.nextDouble() * 2 * StrictMath.PI
                east += mp.jumpM * StrictMath.cos(dir)
                north += mp.jumpM * StrictMath.sin(dir)
                nextJump = t + mp.meanIntervalS * (0.5 + rnd.nextDouble())
            }
            val measured = GenMath.offset(lat, lon, east, north)
            val acc = sigma * (0.6 + rnd.nextDouble())
            val vSigma = sigma * sc.noise.verticalFactor
            val alt = altitudeAt(sc, s) + vSigma * uv
            val spd = if (v > 0) maxOf(0.0, v + 0.15 * gauss.next()) else StrictMath.abs(0.08 * gauss.next())
            val brg = if (v > 0) GenMath.normalizeBearing(heading + 2.0 * gauss.next()) else rnd.nextDouble() * 360.0
            events += RawEvent.Loc(
                mono(t), wall(sc, t), measured[0], measured[1], acc, alt, vSigma * (0.6 + rnd.nextDouble()), spd, brg,
            )
            fixTimes += t
            if (firstGood.isNaN() && acc <= MAX_ACCURACY_M) firstGood = t
            lastFix = t
        }
        sc.barometer?.let { baro ->
            var t = 0.5
            while (t < finishS) {
                val p = GenMath.altitudeToPressure(altitudeAt(sc, motion.distanceAt(t))) +
                    baro.driftHpaPerHour * t / 3600.0 + baro.noiseHpa * gauss.next()
                events += RawEvent.Baro(mono(t), p)
                t += 1.0
            }
        }
        if (sc.heartRate) {
            var t = 0.7
            while (t < finishS) {
                val bpm = 145 + StrictMath.round(10 * GenMath.sin(t / 120.0)).toInt() + rnd.nextInt(-2, 3)
                events += RawEvent.Hr(mono(t), bpm)
                t += 1.0
            }
        }
        val ordered = events.sortedWith(compareBy<RawEvent> { it.t }.thenBy { kindOrder(it) })
        return Generated(sc, ordered, Truth(firstGood, lastFix, finishS, path, motion, actions, fixTimes))
    }

    private fun kindOrder(e: RawEvent): Int = when (e) {
        is RawEvent.Act -> 0
        is RawEvent.Baro -> 1
        is RawEvent.Hr -> 2
        is RawEvent.Loc -> 3
    }

    fun mono(seconds: Double): Long = START_MONO_NANOS + StrictMath.round(seconds * 1e9)

    private fun wall(sc: Scenario, t: Double): Long =
        sc.startEpochMillis + StrictMath.round(t * 1000.0) + sc.wallJumps.filter { it.atS <= t }.sumOf { it.deltaMillis }

    private fun sigmaAt(sc: Scenario, t: Double): Double {
        val w = sc.warmup ?: return sc.noise.sigmaM
        if (t >= w.seconds) return sc.noise.sigmaM
        return sc.noise.sigmaM + (w.startSigmaM - sc.noise.sigmaM) * GenMath.exp(-4.0 * t / w.seconds)
    }

    fun altitudeAt(sc: Scenario, s: Double): Double {
        val knots = sc.elevation
        if (s <= knots.first().distanceM) return knots.first().altitudeM
        for (i in 1 until knots.size) {
            val a = knots[i - 1]
            val b = knots[i]
            if (s <= b.distanceM) return a.altitudeM + (b.altitudeM - a.altitudeM) * (s - a.distanceM) / (b.distanceM - a.distanceM)
        }
        return knots.last().altitudeM
    }

    /** Exact climb and descent of the piecewise-linear profile between distances [from] and [to]. */
    fun elevationTruth(sc: Scenario, from: Double, to: Double): Pair<Double, Double> {
        val xs = (listOf(from, to) + sc.elevation.map { it.distanceM }.filter { it > from && it < to }).sorted()
        var gain = 0.0
        var loss = 0.0
        for (i in 1 until xs.size) {
            val d = altitudeAt(sc, xs[i]) - altitudeAt(sc, xs[i - 1])
            if (d > 0) gain += d else loss -= d
        }
        return gain to loss
    }

    /** Unit-variance first-order Gauss–Markov process. */
    private class GaussMarkov(private val tau: Double) {
        private var x = Double.NaN

        fun step(dt: Double, g: Gaussian): Double {
            x = if (x.isNaN()) {
                g.next()
            } else {
                val rho = GenMath.exp(-dt / tau)
                rho * x + GenMath.sqrt(1 - rho * rho) * g.next()
            }
            return x
        }
    }
}
