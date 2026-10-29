package routeexport

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic synthetic activities for demos and tests. */
object SampleData {
    fun lakeLoop(
        centerLat: Double = 17.4239,
        centerLon: Double = 78.4738,
        radiusDeg: Double = 0.016,
        rotation: Double = 0.0,
        seed: Double = 0.0,
        n: Int = 1400,
    ): Track {
        val pts = ArrayList<TrackPoint>(n + 1)
        var t = 0.0
        var prev: TrackPoint? = null
        for (i in 0..n) {
            val th = 2 * PI * i / n
            val r = radiusDeg * (1 + 0.22 * sin(3 * th + 0.4 + seed) + 0.09 * sin(7 * th + 1.3 + seed * 2) + 0.035 * sin(13 * th + seed))
            val a = th + rotation
            val lat = centerLat + r * sin(a)
            val lon = centerLon + r * cos(a) / cos(Math.toRadians(centerLat))
            val ele = 520 + 18 * sin(2 * th + 0.8 + seed) + 7 * sin(5 * th) + 2 * sin(23 * th)
            val paceSecPerKm = 300 + 28 * sin(4 * th + seed) - 14 * cos(9 * th)
            val hr = (150 + 12 * sin(4 * th + 0.5 + seed) + 5 * sin(11 * th)).toInt()
            val p0 = TrackPoint(lat, lon, ele)
            if (prev != null) t += Geo.haversine(prev, p0) / (1000.0 / paceSecPerKm)
            val p = TrackPoint(lat, lon, ele, t, hr)
            pts += p
            prev = p
        }
        return Track(pts, "Sunrise Lake Loop", "Run · Thu 29 Oct 2026 · 6:12 AM", Sport.RUN)
    }

    fun variants(count: Int = 16): List<Track> = (0 until count).map { k ->
        lakeLoop(
            centerLat = 17.4239 + 0.006 * sin(k * 1.7),
            centerLon = 78.4738 + 0.006 * cos(k * 2.3),
            radiusDeg = 0.011 + 0.008 * ((k * 37) % 10) / 10.0,
            rotation = k * 0.45,
            seed = k * 0.9,
            n = 900,
        )
    }
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "samples").apply { mkdirs() }
    val loop = SampleData.lakeLoop()
    val files = linkedMapOf(
        "01-minimal-white.svg" to RouteArt.minimal(loop),
        "02-minimal-ink.svg" to RouteArt.minimal(loop, color = "#111827"),
        "03-aurora-pace.svg" to RouteArt.aurora(loop),
        "04-aurora-heart-rate-dusk.svg" to RouteArt.aurora(loop, theme = Themes.DUSK, colorBy = ColorBy.HEART_RATE),
        "05-poster-midnight.svg" to RouteArt.poster(loop),
        "06-poster-paper-elevation.svg" to RouteArt.poster(loop, theme = Themes.PAPER, colorBy = ColorBy.ELEVATION),
        "07-poster-forest-imperial.svg" to RouteArt.poster(loop, theme = Themes.FOREST, units = Units.IMPERIAL),
        "08-story-transparent.svg" to RouteArt.story(loop),
        "09-story-dusk.svg" to RouteArt.story(loop, theme = Themes.DUSK, transparent = false),
        "10-elevation.svg" to RouteArt.elevationProfile(loop),
        "11-collage.svg" to RouteArt.collage(SampleData.variants(), title = "2026 in Runs"),
        "12-privacy-trimmed.svg" to RouteArt.aurora(loop, privacyRadiusM = 500.0),
    )
    for ((name, svg) in files) File(out, name).writeText(svg)
    println("Wrote ${files.size} samples to ${out.absolutePath}")
}
