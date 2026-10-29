// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.corpus

import org.openactivity.model.SportType
import org.openactivity.recording.Action

/**
 * The synthetic GPS corpus. IDs follow the scenario catalogue in
 * docs/99-gaps/gps-qa-test-performance.md §2.3; tolerances follow its §3.3 policy.
 *
 * Changing anything here changes the generated files: regenerate with
 * `./gradlew :android:core:recording:generateGpsCorpus` and review the golden diff.
 */
internal object Scenarios {
    /** 2026-06-01T07:00:00Z. */
    private const val JUNE_MORNING = 1_780_297_200_000L

    private val gpsElevationTolerance = ElevationTolerance(pctOver = 25.0, pctUnder = 10.0, absM = 10.0)
    private val baroElevationTolerance = ElevationTolerance(pctOver = 5.0, pctUnder = 5.0, absM = 5.0)

    private fun flat(altitude: Double) = listOf(Knot(0.0, altitude), Knot(1_000_000.0, altitude))

    val all: List<Scenario> = listOf(
        Scenario(
            id = "S001",
            name = "constant-pace-straight-run",
            description = "Straight 5.2 km run at a constant 5:00/km, sigma 4 m.",
            sport = SportType.RUN, seed = 1001, tags = listOf("baseline", "best-efforts"),
            startLat = 47.3667, startLon = 8.5500, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(60.0, 5_300.0)),
            phases = listOf(Phase.Move(5_200.0, 1000.0 / 300.0)),
            elevation = flat(410.0),
            noise = Noise(sigmaM = 4.0),
            autoPause = false,
            tolerances = Tolerances(distancePct = 1.0),
            bestEffortTargets = listOf("1k" to 1000.0, "5k" to 5000.0),
        ),
        Scenario(
            id = "S004",
            name = "urban-canyon-run",
            description = "5.4 km city-grid run with 30 m multipath jumps about every 20 s, sigma 6 m.",
            sport = SportType.RUN, seed = 1004, tags = listOf("noise", "multipath", "urban"),
            startLat = 40.7484, startLon = -73.9857, startEpochMillis = JUNE_MORNING,
            legs = cityGrid(blocks = 26, blockM = 210.0),
            phases = listOf(Phase.Move(5_400.0, 3.0)),
            elevation = flat(20.0),
            noise = Noise(sigmaM = 6.0),
            multipath = Multipath(meanIntervalS = 20.0, jumpM = 30.0),
            autoPause = false,
            tolerances = Tolerances(distancePct = 3.0, splitEachS = 6.0),
        ),
        Scenario(
            id = "S005",
            name = "stationary-jitter",
            description = "Standing still for 10 min with auto-pause on; sigma 10 m correlated drift.",
            sport = SportType.RUN, seed = 1005, tags = listOf("jitter", "auto-pause"),
            startLat = 51.5007, startLon = -0.1246, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(0.0, 10.0)),
            phases = listOf(Phase.Stop(600.0)),
            elevation = flat(15.0),
            noise = Noise(sigmaM = 10.0),
            autoPause = true,
            tolerances = Tolerances(distancePct = 0.0),
        ),
        Scenario(
            id = "S006",
            name = "stop-and-go-traffic-ride",
            description = "12.35 km ride with 12 traffic-light stops of 20-90 s, auto-pause on, sigma 4 m.",
            sport = SportType.RIDE, seed = 1006, tags = listOf("auto-pause", "stops"),
            startLat = 52.5200, startLon = 13.4050, startEpochMillis = JUNE_MORNING,
            legs = listOf(
                Leg.Straight(90.0, 3_100.0), Leg.Turn(45.0, 30.0), Leg.Straight(135.0, 3_100.0),
                Leg.Turn(-90.0, 30.0), Leg.Straight(45.0, 3_100.0), Leg.Turn(45.0, 30.0), Leg.Straight(90.0, 3_100.0),
            ),
            phases = stopAndGo(),
            elevation = flat(35.0),
            noise = Noise(sigmaM = 4.0),
            autoPause = true,
            tolerances = Tolerances(distancePct = 1.0, movingPct = 3.0, splitEachS = 3.0),
            checkMaxSpeed = true,
        ),
        Scenario(
            id = "S007",
            name = "ride-with-descents",
            description = "8 km switchback climb (+400 m) then 8 km descent at 14-20 m/s, GPS altitude only.",
            sport = SportType.RIDE, seed = 1007, tags = listOf("descent", "elevation-gps", "switchbacks"),
            startLat = 46.0207, startLon = 7.7491, startEpochMillis = JUNE_MORNING,
            legs = switchbacks(legs = 13, legM = 620.0, radiusM = 10.0, firstBearing = 20.0) + windingRoad(legs = 12, legM = 680.0),
            phases = listOf(
                Phase.Move(8_000.0, 5.0), Phase.Move(3_000.0, 16.0), Phase.Move(2_000.0, 20.0), Phase.Move(3_000.0, 14.0),
            ),
            elevation = listOf(Knot(0.0, 1_300.0), Knot(8_000.0, 1_700.0), Knot(16_000.0, 1_300.0)),
            noise = Noise(sigmaM = 4.0),
            autoPause = true,
            tolerances = Tolerances(distancePct = 1.0, elevation = gpsElevationTolerance, splitEachS = 3.0),
            checkMaxSpeed = true,
        ),
        Scenario(
            id = "S008",
            name = "tunnel-ride",
            description = "8 km ride at 54 km/h through a 2.1 km tunnel: no fix for 140 s.",
            sport = SportType.RIDE, seed = 1008, tags = listOf("gap", "tunnel"),
            startLat = 46.6000, startLon = 8.6000, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(200.0, 8_100.0)),
            phases = listOf(Phase.Move(8_000.0, 15.0)),
            elevation = flat(1_100.0),
            noise = Noise(sigmaM = 4.0),
            gaps = listOf(Window(2_000.0 / 15.0, 2_000.0 / 15.0 + 140.0)),
            autoPause = true,
            tolerances = Tolerances(distancePct = 5.0, splitEachS = 3.0),
        ),
        Scenario(
            id = "S015",
            name = "dst-wall-clock-jump-ride",
            description = "20 min ride across the EU DST switch (+1 h wall jump) plus a -90 s NTP correction.",
            sport = SportType.RIDE, seed = 1015, tags = listOf("clock", "dst", "ntp"),
            startLat = 48.8566, startLon = 2.3522,
            startEpochMillis = 1_774_745_700_000L, // 2026-03-29T00:55:00Z, DST starts at 01:00Z
            legs = listOf(Leg.Straight(0.0, 4_000.0), Leg.Turn(120.0, 50.0), Leg.Straight(120.0, 6_000.0)),
            phases = listOf(Phase.Move(9_600.0, 8.0)),
            elevation = flat(35.0),
            noise = Noise(sigmaM = 4.0),
            wallJumps = listOf(WallJump(600.0, 3_600_000L), WallJump(900.0, -90_000L)),
            autoPause = true,
            tolerances = Tolerances(distancePct = 1.0),
        ),
        Scenario(
            id = "S022",
            name = "walk-with-long-stop",
            description = "3.4 km walk at 1.3 m/s with a 15 min stop; auto-pause off, sigma 5 m.",
            sport = SportType.WALK, seed = 1022, tags = listOf("jitter", "moving-time", "slow"),
            startLat = 41.9028, startLon = 12.4964, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(10.0, 1_700.0), Leg.Turn(90.0, 15.0), Leg.Straight(100.0, 1_720.0)),
            phases = listOf(Phase.Move(1_700.0, 1.3), Phase.Stop(900.0), Phase.Move(1_700.0, 1.3)),
            elevation = flat(40.0),
            noise = Noise(sigmaM = 5.0),
            autoPause = false,
            tolerances = Tolerances(distancePct = 1.0, splitEachS = 5.0),
        ),
        Scenario(
            id = "S023",
            name = "switchback-climb-barometer",
            description = "3.4 km hike up 13 switchbacks, +340 m, barometer with weather drift.",
            sport = SportType.HIKE, seed = 1023, tags = listOf("elevation-baro", "switchbacks", "climb"),
            startLat = 46.5580, startLon = 7.9800, startEpochMillis = JUNE_MORNING,
            legs = switchbacks(legs = 14, legM = 250.0, radiusM = 6.0, firstBearing = 300.0),
            phases = listOf(Phase.Move(3_400.0, 1.1)),
            elevation = listOf(Knot(0.0, 1_000.0), Knot(3_400.0, 1_340.0)),
            noise = Noise(sigmaM = 4.0),
            barometer = Barometer(noiseHpa = 0.05, driftHpaPerHour = 0.5),
            autoPause = false,
            tolerances = Tolerances(distancePct = 1.0, elevation = baroElevationTolerance, splitEachS = 8.0),
        ),
        Scenario(
            id = "S028",
            name = "manual-laps-run",
            description = "4.4 km run, lap every km plus two presses 0.6 s apart, manual pause during a 70 s stop.",
            sport = SportType.RUN, seed = 1028, tags = listOf("laps", "manual-pause", "heart-rate"),
            startLat = 59.3293, startLon = 18.0686, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(135.0, 2_200.0), Leg.Turn(-90.0, 20.0), Leg.Straight(45.0, 2_300.0)),
            phases = listOf(Phase.Move(2_500.0, 3.6), Phase.Stop(70.0), Phase.Move(1_900.0, 3.6)),
            elevation = flat(25.0),
            noise = Noise(sigmaM = 4.0),
            heartRate = true,
            autoPause = false,
            tolerances = Tolerances(distancePct = 1.0, lapDistancePct = 2.5),
            actions = { m ->
                val stop = m.stops.single()
                listOf(
                    TimedAction(m.timeAtDistance(1_000.0), Action.LAP),
                    TimedAction(m.timeAtDistance(2_000.0), Action.LAP),
                    TimedAction(m.timeAtDistance(2_000.0) + 0.6, Action.LAP),
                    TimedAction(stop.fromS + 3.0, Action.PAUSE),
                    TimedAction(stop.toS - 3.0, Action.RESUME),
                    TimedAction(m.timeAtDistance(3_000.0), Action.LAP),
                )
            },
        ),
        Scenario(
            id = "S029",
            name = "low-accuracy-start",
            description = "Start before GPS is ready: sigma decays from 80 m to 4 m over 90 s while running.",
            sport = SportType.RUN, seed = 1029, tags = listOf("warm-up", "accuracy"),
            startLat = 35.6762, startLon = 139.6503, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(250.0, 2_200.0), Leg.Turn(90.0, 25.0), Leg.Straight(340.0, 2_300.0)),
            phases = listOf(Phase.Move(4_400.0, 3.0)),
            elevation = flat(40.0),
            noise = Noise(sigmaM = 4.0),
            warmup = Warmup(seconds = 90.0, startSigmaM = 80.0),
            autoPause = false,
            tolerances = Tolerances(distancePct = 2.5, splitEachS = 5.0),
        ),
        Scenario(
            id = "S032",
            name = "antimeridian-ride",
            description = "6.4 km ride eastwards across the 180 degree meridian (Taveuni, Fiji).",
            sport = SportType.RIDE, seed = 1032, tags = listOf("antimeridian", "geometry"),
            startLat = -16.7900, startLon = 179.9850, startEpochMillis = JUNE_MORNING,
            legs = listOf(Leg.Straight(95.0, 6_500.0)),
            phases = listOf(Phase.Move(6_400.0, 6.0)),
            elevation = flat(30.0),
            noise = Noise(sigmaM = 4.0),
            autoPause = true,
            tolerances = Tolerances(distancePct = 1.0),
            checkAntimeridian = true,
        ),
    )

    /** A staircase of city blocks with 90 degree corners of radius 8 m. */
    private fun cityGrid(blocks: Int, blockM: Double): List<Leg> = buildList {
        var bearing = 30.0
        repeat(blocks) { i ->
            add(Leg.Straight(bearing, blockM))
            val turn = if (i % 2 == 0) 90.0 else -90.0
            add(Leg.Turn(turn, 8.0))
            bearing = GenMath.normalizeBearing(bearing + turn)
        }
    }

    /** Hairpin switchbacks: straight legs joined by 180 degree turns alternating left and right. */
    private fun switchbacks(legs: Int, legM: Double, radiusM: Double, firstBearing: Double): List<Leg> = buildList {
        var bearing = firstBearing
        repeat(legs) { i ->
            add(Leg.Straight(bearing, legM))
            if (i < legs - 1) {
                val turn = if (i % 2 == 0) 180.0 else -180.0
                add(Leg.Turn(turn, radiusM))
                bearing = GenMath.normalizeBearing(bearing + turn)
            }
        }
    }

    /** A descending road with 90 degree bends of radius 40 m. */
    private fun windingRoad(legs: Int, legM: Double): List<Leg> = buildList {
        var bearing = 200.0
        add(Leg.Turn(-90.0, 40.0))
        bearing = GenMath.normalizeBearing(bearing - 90.0)
        repeat(legs) { i ->
            add(Leg.Straight(bearing, legM))
            val turn = if (i % 2 == 0) 90.0 else -90.0
            add(Leg.Turn(turn, 40.0))
            bearing = GenMath.normalizeBearing(bearing + turn)
        }
    }

    /** Stops every 950 m, so none falls within 50 m of a kilometre split boundary (which would be ambiguous). */
    private fun stopAndGo(): List<Phase> {
        val stops = listOf(20.0, 35.0, 50.0, 90.0, 25.0, 60.0, 45.0, 30.0, 75.0, 40.0, 55.0, 65.0)
        val speeds = listOf(7.5, 8.5, 9.0, 8.0)
        return buildList {
            stops.forEachIndexed { i, s ->
                add(Phase.Move(950.0, speeds[i % speeds.size]))
                add(Phase.Stop(s))
            }
            add(Phase.Move(950.0, speeds[stops.size % speeds.size]))
        }
    }
}
