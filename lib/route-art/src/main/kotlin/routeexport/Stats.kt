package routeexport

import kotlin.math.roundToInt

data class Stats(
    val distanceM: Double,
    val elapsedSec: Double?,
    val movingSec: Double?,
    val elevGainM: Double?,
    val minEleM: Double?,
    val maxEleM: Double?,
    val avgHr: Int?,
)

private const val MOVING_SPEED_MPS = 0.6
private const val ELEVATION_HYSTERESIS_M = 3.0

fun Track.stats(): Stats = summary ?: statsOf(points)

fun statsOf(points: List<TrackPoint>): Stats {
    val hasTime = points.size > 1 && points.all { it.time != null }
    var dist = 0.0
    var moving = 0.0
    for (i in 1 until points.size) {
        val d = Geo.haversine(points[i - 1], points[i])
        dist += d
        if (hasTime) {
            val dt = points[i].time!! - points[i - 1].time!!
            if (dt > 0 && d / dt >= MOVING_SPEED_MPS) moving += dt
        }
    }

    val eles = points.mapNotNull { it.ele }
    var gain: Double? = null
    if (eles.size > 1) {
        // Hysteresis filter: ignore wiggles smaller than the threshold (GPS/baro noise).
        var g = 0.0
        var ref = eles[0]
        for (e in eles) {
            if (e > ref + ELEVATION_HYSTERESIS_M) { g += e - ref; ref = e }
            else if (e < ref - ELEVATION_HYSTERESIS_M) ref = e
        }
        gain = g
    }

    val hrs = points.mapNotNull { it.hr }
    return Stats(
        distanceM = dist,
        elapsedSec = if (hasTime) points.last().time!! - points.first().time!! else null,
        movingSec = if (hasTime) moving else null,
        elevGainM = gain,
        minEleM = eles.minOrNull(),
        maxEleM = eles.maxOrNull(),
        avgHr = if (hrs.isEmpty()) null else hrs.average().roundToInt(),
    )
}
