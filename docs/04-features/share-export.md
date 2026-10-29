# Sharing & Export (P0)

Status: spec draft · Priority: **P0 (must-have)** · Owner: StravaFit core
Scope: Part A documents how Strava shares and exports today, for reference only. Part B is the StravaFit spec, which aims to beat it.
Legend: `[FREE]` / `[SUB]` show Strava's gating. **In StravaFit every feature is free.** `(unverified)` means no primary source has confirmed the item yet.

---

## Part A: Strava as-is

### A.1 Entry points to sharing

| Entry point | Where | What it produces | Gate |
|---|---|---|---|
| Share icon on Activity Detail (top-right, Android share glyph) | Activity screen | Opens Strava's share sheet (stickers/templates + targets) | [FREE] |
| Post-save "Share your activity" prompt | After Save on Record flow | Same share sheet, pre-selected template (unverified) | [FREE] |
| Feed card overflow (⋮) → Share | Home feed | Share sheet / copy link | [FREE] |
| Route detail → Share | Maps tab → Routes | Route link and (on web) GPX/TCX export | [FREE] link; route GPX export (unverified gating, historically free) |
| Year in Sport | Yearly recap (Dec) | Swipeable story cards, each shareable | Basic [FREE]; fuller recap [SUB] (unverified) |
| Club / Challenge pages | Groups tab | Invite link, joined/earned trophy share card | [FREE] |
| Segment / Best effort / PR | Segment efforts | Link share | [FREE] |
| Profile → Share profile | Profile | Profile link / QR (unverified) | [FREE] |
| Flyover (3D replay) → Share | Activity Detail or Flyover screen | 3D flyover video to IG Stories, or a share link via "More". GPS activities on your own profile, past ones included. A push notification arrives when the video is ready | [SUB] (support.strava.com "Flyover"; GCN, road.cc) |

**Visibility rule:** you cannot share activities marked **Only You** (private) as stickers or links. They must be set to Everyone or Followers first (BikeRadar, 2025).

### A.2 Share targets

Strava hands off through Android's system share sheet plus a row of first-party shortcuts:
- **Instagram Stories**: opens Instagram's story composer with a stats sticker layered over a chosen background or photo. You can move and resize the sticker in Instagram.
- **Instagram Feed post** (image card), **Facebook** (link with OG preview, or image), **WhatsApp**, **X/Twitter**, **Snapchat** (unverified), **Messenger**, **Copy link**, **Save image / Download**, **More…** (system chooser).
- **Copy sticker to clipboard**: "Sticker Stats" (spring 2025). You copy a transparent sticker and paste it onto any photo in any app.

### A.3 Shareable visual templates (share sheet carousel)

Swipe horizontally between template cards (as observed in walkthroughs, unverified naming):
1. **Map card**: route on a basemap with title and 3 stats. Opaque.
2. **Photo + stats overlay**: your activity photo with stats along the bottom and a small route trace.
3. **Stats-only / Sticker Stats**: transparent PNG with distance, elevation gain, time and a line drawing of the route (BikeRadar).
4. **Route-trace only**: transparent outline of the route, in white or Strava orange (unverified as a separate card).
5. **Splits / chart card**: pace or HR mini-chart, gated by data availability (unverified).
6. **Achievement card**: PR / KOM / Local Legend badge when earned (unverified).

Customization (limited compared to what we plan):
- Pick which photo (from the activity's media).
- Stats are mostly fixed per sport type (Run: distance, pace, time; Ride: distance, elev, time). Some versions let you cycle stats by tapping (unverified).
- Light or dark variant of the sticker (unverified).
- No custom colors, fonts, gradient-by-metric, or vector export.

### A.4 Share-sheet wireframe (Strava, approximated)

```
+--------------------------------------+
| X              Share                 |
|--------------------------------------|
|  < [  Map card preview   ]  >        |
|    [  route on map       ]           |
|    [  10.2 km 5:12 52:40 ]           |
|        o  o  *  o  o   (pager)       |
|--------------------------------------|
|  [IG Story] [IG Post] [FB] [WA]      |
|  [ X ]  [Copy link] [Save] [More..]  |
|--------------------------------------|
|  [Copy sticker]  (Sticker Stats)     |
+--------------------------------------+
```

### A.5 Activity link sharing and previews

- Canonical URL: `https://www.strava.com/activities/{id}`. On Android, short links are made through the share action (`strava.app.link/...` branch link, unverified).
- **Open Graph preview:** `og:title` (activity name), `og:description` (distance, time, athlete), and `og:image`, a server-rendered static map with the route polyline. WhatsApp, Slack, iMessage, Facebook and X all show it. Followers-only or private activities show a generic Strava card or a login wall (unverified).
- **Deep linking:** links open in the Strava app if it is installed (Android App Links).

### A.6 Embeds on the web

- **Activity embed** (web only): on the activity page, `…` → **Embed on blog** gives an `<iframe>` or script snippet that renders a map and stats card. The activity must be Everyone-visible (unverified detail).
- **Route embed** (web): an iframe for route maps (unverified on current site).
- **Profile / club widgets**: legacy "latest rides/runs" widgets under strava.com/widgets. Historically available but now unmaintained (unverified).

### A.7 File export

| Export | How | Format | Gate |
|---|---|---|---|
| Single activity | Web: Activity → `…` → **Export GPX** | GPX (Strava-generated, drops some sensor streams) | [FREE] |
| Single activity original | Web: `…` → **Export Original** | Exact uploaded file (FIT/TCX/GPX) | [FREE] |
| Single activity TCX | Web URL `/activities/{id}/export_tcx` | TCX (unverified, not in UI) | [FREE] |
| Route | Web/app Route detail → Export GPX / TCX. GPX Track for devices, GPX Route for planners | GPX, TCX | Historically [FREE]. Route creation features are [SUB] |
| Bulk account archive | Web: Settings → My Account → **Download or Delete Your Account** → Request archive. You get an emailed zip | `activities/*.fit/.gpx(.gz)` (original format, named by ID), `activities.csv`, `media/`, `routes/`, `segments.csv`, profile JSON/CSV | [FREE] (GDPR) |
| API | developers.strava.com: `GET /activities/{id}/streams`, `GET /routes/{id}/export_gpx`, `export_tcx` | JSON streams, GPX/TCX | Per-app OAuth, rate-limited |

Notes:
- **The Android app does not offer GPX export of activities.** You have to use the website, which is a common complaint on Reddit (unverified as of 2026).
- Bulk archives keep the original upload format (FIT or GPX, some gzipped). Before GDPR, Strava gave all-GPX exports (per the fit2gpx project docs).

### A.8 Recap and social cards

- **Year in Sport**: personalized story-style cards (totals, top sport, most active month, kudos). Each card is shareable to IG Stories or saveable. Strava gates part of the recap behind [SUB] (unverified exact split).
- **Monthly/weekly progress**: [SUB] training log / progress share (unverified).
- **Club share card**: invite link and preview. **Challenge**: joined or completed trophy card with badge art.
- **Milestone / streak cards**: auto prompts (unverified).

### A.9 Privacy interaction with shared maps

- **Hide start/end**: up to a 1-mile radius, either at a specific address (applies to past and future activities) or everywhere (future only). Shared maps, OG images, stickers and embeds all show the **trimmed** polyline. The owner's in-app view shows the hidden part in grey (DC Rainmaker / the5krunner, 2021).
- **Hide map entirely**: no map in shares, a stats-only card, and the activity is excluded from the Global Heatmap.
- **Activity visibility** (Everyone / Followers / Only You): controls link and preview access and whether you can share at all.
- Segments inside hidden zones don't match for other viewers.

### A.10 Gaps in Strava we will exploit

No vector (SVG/PDF) export · animated/3D Flyover video is [SUB]-only (ours free: P2 animated MP4, see open questions) · no gradient-by-metric route art · no export from the Android app · no poster or collage · no GeoJSON or FIT re-export · limited stat choice · no accessibility text on images · no offline sticker making for private activities.

---

## Part B: StravaFit spec (exceed Strava)

### B.1 Requirements

| ID | Requirement | Pri |
|---|---|---|
| SE-1 | Share any activity (including **private** ones, rendered locally) as image, sticker, or link | P0 |
| SE-2 | Export route art as **SVG**, PNG (transparent), PNG (with background), **PDF poster** | P0 |
| SE-3 | Export data as **GPX, TCX, FIT, GeoJSON**, plus the original file | P0 |
| SE-4 | Privacy trimming applied **before** any render or export (opt-out only for the owner's raw data export) | P0 |
| SE-5 | Templates: Minimal, Poster, Story 9:16, Square 1:1, Landscape 16:9, Sticker (transparent) | P0 |
| SE-6 | Color by pace, HR, elevation, power, or speed (gradient) | P1 |
| SE-7 | Optional MapLibre basemap under the vector trace, with OSM attribution | P1 |
| SE-8 | Elevation profile SVG; km/mi markers; start/end markers | P1 |
| SE-9 | Multi-activity collage and "all my runs" heatmap poster | P1 |
| SE-10 | Instagram Stories direct intent, FileProvider share, MediaStore save | P0 |
| SE-11 | Bulk account export (zip) from the app | P0 |
| SE-12 | Web parity (JS/TS renderer, same golden output) | P1 |
| SE-13 | Link share with OG image (self-hosted instance) | P2 |

### B.2 Rendering pipeline

```
raw points (lat,lng,t,ele,hr,pwr) | encoded polyline
   -> decode (if polyline)
   -> privacy trim (privacy zones + hide start/end radius; drop all if map hidden)
   -> Web Mercator projection (lat/lng -> x,y in metres)
   -> simplification (Douglas-Peucker default; Visvalingam for "art" style)
        tolerance = k * (bboxDiag / outputPx)  -> ~0.5 output px
   -> fit to viewBox: uniform scale, padding %, center, flip Y
   -> optional smoothing: Catmull-Rom -> cubic Bezier
   -> styling: segments/gradients by metric, markers, glow
   -> emit: SVG string | Android Canvas -> Bitmap PNG | PdfDocument
```

Rules:
- **Trim before simplify.** Simplifying first could move a vertex back into a privacy zone.
- Trim mode is "cut and fade". Points within radius R of the start, the end, or any zone are removed. With the "fade" option on, the first visible segment gets a 6px alpha ramp (still never revealing hidden points).
- Use the projection on the **trimmed** bbox so the hidden part never affects framing (the shape stays non-inferable).
- Tolerance scales with output size: `epsMeters = 0.5 * metersPerOutputPx`. A 1080px story and a 7016px A2 poster get different detail.
- Very short tracks (<2 points after trimming) get the "map hidden" stats-only card.
- Anti-meridian crossing: unwrap longitudes (add ±360) before projecting.

### B.3 Styles

**Stroke:** width in output px (default `max(4, 0.006*min(w,h))`), `stroke-linecap=round`, `stroke-linejoin=round`, `miterlimit=2`.

**Coloring modes:**
- `SOLID`: one `<path>`.
- `GRADIENT_ALONG` (pace/HR/elev/power/speed): split the path into N runs of consecutive same-bucket segments (quantize the metric to 32 buckets). Emit one `<path>` per run with its own stroke color. This is robust in every renderer. A `linearGradient` only follows a straight axis, so we use it only for "time" gradients along the bbox diagonal.
- Palettes: `pace` (fast = hot), `hr` zones Z1-Z5 (design-system tokens), `elevation` (terrain ramp), `power` zones. Each must pass 3:1 contrast against the chosen background (validator in tests).
- Metric clamp: 5th-95th percentile to avoid outlier blowout.

**Markers:** start = filled circle (success token), end = checkered or ring (danger token), km/mi markers = small circle + label every 1 unit (auto-thin to every 5 or 10 when there are more than 25 markers). The start/end markers sit at **trimmed** endpoints.

**Glow/shadow:** SVG `<filter>` with `feGaussianBlur stdDeviation=w*0.8` + `feMerge`. On Android: `Paint.setShadowLayer` or `BlurMaskFilter` on a software layer.

**Background:** `transparent` | solid token | gradient | photo (PNG/PDF only) | **basemap**.

**Basemap (optional):**
- Use `MapSnapshotter` (MapLibre Native Android) or `maplibregl` with `preserveDrawingBuffer` on the web, sized to the output's map area with the same Web Mercator bounds and padding as the vector fit.
- Place the raster under the vector trace. SVG output embeds the raster as `<image href="data:image/png;base64,…">`, or as a linked file with the "Keep SVG pure vector" toggle.
- **Attribution required:** "© OpenStreetMap contributors" plus the tile/style provider (e.g. OpenFreeMap, MapTiler) appears legibly on the image (≥ 9px at 1080w, contrast ≥ 4.5:1) whenever a basemap is used. You cannot turn it off with a basemap. Use only tile servers whose terms allow static image export (OSMF tile servers forbid bulk use, so don't use tile.openstreetmap.org).

**Elevation profile SVG:** x = cumulative distance, y = elevation, filled area `path` + line. Auto-scale y with 10% headroom, gradient fill by grade (optional), min/max labels.

**Typography:** design-system tokens (`--font-display` for big numbers, `--font-text` for labels). Tabular numerals (`font-variant-numeric: tabular-nums`). SVG embeds `@font-face` as base64 (subset to used glyphs) or converts text to paths (toggle "Outline text" for print shops).

**Templates (layout boxes in output px):**

| Template | Size | Layout |
|---|---|---|
| Minimal | 1080×1080 | Trace centered 80%, one stat line under it |
| Sticker | auto bbox, transparent | Trace + 3 stats, white or dark ink, 24px padding |
| Story 9:16 | 1080×1920 | Safe zones: top 250px, bottom 340px free (IG UI). Trace in middle 60%, stats grid 3-up |
| Square 1:1 | 1080×1080 | Trace left 60%, stats column right |
| Landscape | 1920×1080 | Trace + elevation strip at bottom |
| Poster | A4/A3/A2 PDF + PNG at 300dpi | Title, date, location, trace 70%, elevation strip, stats row, footer with attribution |
| Collage | 1080×1080 or poster | Grid of N mini traces (each normalized), labels optional |
| Heatmap art | poster | All activities overlaid in one projection, additive alpha (0.08-0.2), optional basemap |

Heatmap: render to an offscreen float or alpha buffer with additive blend, then apply a tone-map palette. SVG version: one `<path>` per activity with `stroke-opacity` (>2k paths → offer PNG only, warn about size).

### B.4 Kotlin reference code

Module: `:feature:share` (pure geometry in `:core:geo`, no Android deps, so it's unit-testable on the JVM).

#### B.4.1 Models + polyline decode

```kotlin
package app.stravafit.core.geo

data class LatLng(val lat: Double, val lng: Double)
data class TrackPoint(
    val pos: LatLng,
    val tSec: Double? = null,
    val ele: Double? = null,
    val hr: Int? = null,
    val power: Int? = null,
    val distM: Double? = null,
)

/** Google encoded polyline algorithm (precision 5 default; 6 for OSRM/Valhalla). */
object Polyline {
    fun decode(encoded: String, precision: Int = 5): List<LatLng> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<LatLng>(encoded.length / 4)
        var i = 0; var lat = 0L; var lng = 0L
        while (i < encoded.length) {
            var shift = 0; var result = 0L; var b: Int
            do { b = encoded[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            shift = 0; result = 0L
            do { b = encoded[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1L != 0L) (result shr 1).inv() else (result shr 1)
            out += LatLng(lat / factor, lng / factor)
        }
        return out
    }

    fun encode(points: List<LatLng>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder(); var pLat = 0L; var pLng = 0L
        fun enc(v: Long) { var s = if (v < 0) (v shl 1).inv() else (v shl 1)
            while (s >= 0x20) { sb.append(((0x20 or (s and 0x1f).toInt()) + 63).toChar()); s = s shr 5 }
            sb.append((s.toInt() + 63).toChar()) }
        for (p in points) {
            val la = Math.round(p.lat * factor); val ln = Math.round(p.lng * factor)
            enc(la - pLat); enc(ln - pLng); pLat = la; pLng = ln
        }
        return sb.toString()
    }
}
```

#### B.4.2 Privacy trimming

```kotlin
package app.stravafit.core.geo

import kotlin.math.*

data class PrivacyZone(val center: LatLng, val radiusM: Double)
data class PrivacySettings(
    val hideStartEndM: Double = 0.0,        // 0..1609 (1 mi), StravaFit allows up to 2000
    val zones: List<PrivacyZone> = emptyList(),
    val hideMap: Boolean = false,
)

fun haversineM(a: LatLng, b: LatLng): Double {
    val r = 6_371_008.8
    val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(min(1.0, sqrt(h)))
}

/**
 * Returns visible runs (a track can be split into several by a zone in the middle).
 * Never returns a point within any hidden radius.
 */
fun <T> trimForPrivacy(points: List<T>, pos: (T) -> LatLng, s: PrivacySettings): List<List<T>> {
    if (s.hideMap || points.size < 2) return emptyList()
    val start = pos(points.first()); val end = pos(points.last())
    fun hidden(p: LatLng): Boolean =
        (s.hideStartEndM > 0 && (haversineM(p, start) <= s.hideStartEndM || haversineM(p, end) <= s.hideStartEndM)) ||
        s.zones.any { haversineM(p, it.center) <= it.radiusM }
    val runs = mutableListOf<MutableList<T>>(); var cur: MutableList<T>? = null
    for (pt in points) {
        if (hidden(pos(pt))) { cur = null } else {
            if (cur == null) { cur = mutableListOf(); runs += cur }
            cur.add(pt)
        }
    }
    return runs.filter { it.size >= 2 }
}
```

Note: Strava's "hide start/end" trims along the track from each end. The radius test above also hides mid-track passes near home. That is stricter, and it's the default. A `mode = ENDS_ONLY` variant trims only the leading and trailing prefixes.

#### B.4.3 Projection + simplification + fit

```kotlin
package app.stravafit.core.geo

import kotlin.math.*

data class Pt(val x: Double, val y: Double)

object WebMercator {
    private const val R = 6_378_137.0
    private const val MAX_LAT = 85.05112878
    fun project(p: LatLng): Pt {
        val lat = p.lat.coerceIn(-MAX_LAT, MAX_LAT)
        return Pt(R * Math.toRadians(p.lng), R * ln(tan(PI / 4 + Math.toRadians(lat) / 2)))
    }
    /** Unwrap longitudes so tracks crossing ±180 stay contiguous. */
    fun unwrap(pts: List<LatLng>): List<LatLng> {
        if (pts.isEmpty()) return pts
        val out = ArrayList<LatLng>(pts.size); var offset = 0.0; var prev = pts[0].lng
        for (p in pts) { val d = p.lng - prev
            if (d > 180) offset -= 360 else if (d < -180) offset += 360
            prev = p.lng; out += LatLng(p.lat, p.lng + offset) }
        return out
    }
}

/** Iterative Douglas-Peucker; returns kept indices (stable). */
fun douglasPeucker(pts: List<Pt>, eps: Double): IntArray {
    if (pts.size < 3) return IntArray(pts.size) { it }
    val keep = BooleanArray(pts.size); keep[0] = true; keep[pts.lastIndex] = true
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to pts.lastIndex) }
    val eps2 = eps * eps
    while (stack.isNotEmpty()) {
        // removeAt(lastIndex), not removeLast(): with JDK 21 + Kotlin 2 the latter binds to
        // java.util.List.removeLast and crashes (NoSuchMethodError) on Android < 15.
        val (a, b) = stack.removeAt(stack.lastIndex); var maxD = -1.0; var idx = -1
        for (i in a + 1 until b) { val d = segDist2(pts[i], pts[a], pts[b]); if (d > maxD) { maxD = d; idx = i } }
        if (maxD > eps2 && idx > 0) { keep[idx] = true; stack.add(a to idx); stack.add(idx to b) }
    }
    return keep.indices.filter { keep[it] }.toIntArray()
}

private fun segDist2(p: Pt, a: Pt, b: Pt): Double {
    val dx = b.x - a.x; val dy = b.y - a.y
    if (dx == 0.0 && dy == 0.0) return (p.x - a.x).pow(2) + (p.y - a.y).pow(2)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
    val qx = a.x + t * dx; val qy = a.y + t * dy
    return (p.x - qx).pow(2) + (p.y - qy).pow(2)
}

/** Visvalingam-Whyatt by min effective area (O(n log n) with heap). Smoother "art" look. */
fun visvalingam(pts: List<Pt>, minArea: Double): IntArray {
    val n = pts.size; if (n < 3) return IntArray(n) { it }
    val prev = IntArray(n) { it - 1 }; val next = IntArray(n) { it + 1 }
    val area = DoubleArray(n) { Double.POSITIVE_INFINITY }
    fun tri(i: Int) = if (prev[i] < 0 || next[i] >= n) Double.POSITIVE_INFINITY else
        abs((pts[prev[i]].x - pts[i].x) * (pts[next[i]].y - pts[i].y) - (pts[next[i]].x - pts[i].x) * (pts[prev[i]].y - pts[i].y)) / 2
    val pq = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
    for (i in 1 until n - 1) { area[i] = tri(i); pq += area[i] to i }
    val removed = BooleanArray(n)
    while (pq.isNotEmpty()) {
        val (a, i) = pq.poll(); if (removed[i] || a != area[i]) continue
        if (a >= minArea) break
        removed[i] = true; val p = prev[i]; val nx = next[i]
        next[p] = nx; prev[nx] = p
        for (j in intArrayOf(p, nx)) if (j in 1 until n - 1 && !removed[j]) {
            area[j] = max(tri(j), a) /* monotonic */; pq += area[j] to j }
    }
    return (0 until n).filter { !removed[it] }.toIntArray()
}

data class Box(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val w get() = maxX - minX; val h get() = maxY - minY
}
fun bbox(runs: List<List<Pt>>): Box {
    var a = Double.MAX_VALUE; var b = Double.MAX_VALUE; var c = -Double.MAX_VALUE; var d = -Double.MAX_VALUE
    runs.forEach { r -> r.forEach { a = min(a, it.x); b = min(b, it.y); c = max(c, it.x); d = max(d, it.y) } }
    return Box(a, b, c, d)
}

/** Fit projected metres into a target rect (output px), uniform scale, centered, Y flipped. */
class Fitter(src: Box, private val dst: Box, paddingFrac: Double = 0.08) {
    private val padX = dst.w * paddingFrac; private val padY = dst.h * paddingFrac
    val scale = min((dst.w - 2 * padX) / max(src.w, 1e-9), (dst.h - 2 * padY) / max(src.h, 1e-9))
    private val ox = dst.minX + (dst.w - src.w * scale) / 2 - src.minX * scale
    private val oy = dst.minY + (dst.h - src.h * scale) / 2 + src.maxY * scale
    fun map(p: Pt) = Pt(ox + p.x * scale, oy - p.y * scale)
    /** metres per output pixel, for tolerance selection */
    val metresPerPx get() = 1.0 / scale
}
```

#### B.4.4 Smoothing (Catmull-Rom to cubic Bezier)

```kotlin
package app.stravafit.core.geo

/** Uniform Catmull-Rom (NOT centripetal) -> cubic Bezier. tension 0.5 = standard CR: c1 = p1 + (p2-p0)/6. */
fun catmullRomToBezier(p: List<Pt>, tension: Double = 0.5): List<Array<Pt>> {
    if (p.size < 3) return emptyList()
    val k = tension / 3.0 // 0.5 -> 1/6, matches the TS port's hard-coded /6
    val segs = ArrayList<Array<Pt>>(p.size - 1)
    for (i in 0 until p.lastIndex) {
        val p0 = p[maxOf(i - 1, 0)]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[minOf(i + 2, p.lastIndex)]
        val c1 = Pt(p1.x + (p2.x - p0.x) * k, p1.y + (p2.y - p0.y) * k)
        val c2 = Pt(p2.x - (p3.x - p1.x) * k, p2.y - (p3.y - p1.y) * k)
        segs += arrayOf(p1, c1, c2, p2)
    }
    return segs
}
```

#### B.4.5 Render model + SVG builder

```kotlin
package app.stravafit.feature.share.render

import app.stravafit.core.geo.*
import java.util.Locale

enum class ColorBy { SOLID, PACE, HR, ELEVATION, POWER, SPEED }
enum class Simplifier { DOUGLAS_PEUCKER, VISVALINGAM }

data class TraceStyle(
    val strokePx: Float = 8f,
    val color: Int = 0xFFFC5200.toInt(),        // design token: brand.route
    val colorBy: ColorBy = ColorBy.SOLID,
    val palette: IntArray = Palettes.PACE,
    val smooth: Boolean = false,
    val glow: Boolean = false,
    val startEndMarkers: Boolean = true,
    val distanceMarkersEveryM: Double? = null, // 1000.0 or 1609.344
)

data class Background(val transparent: Boolean = true, val color: Int = 0xFF101114.toInt(), val basemapPngBase64: String? = null)

data class RenderSpec(
    val widthPx: Int, val heightPx: Int,
    val traceRect: Box,                       // where trace is drawn in output px
    val padding: Double = 0.08,
    val simplifier: Simplifier = Simplifier.DOUGLAS_PEUCKER,
    val toleranceOutputPx: Double = 0.5,
    val style: TraceStyle = TraceStyle(),
    val bg: Background = Background(),
    val attribution: String? = null,          // REQUIRED non-null when bg.basemap != null
    val title: String? = null,
    val stats: List<Pair<String, String>> = emptyList(), // label -> value (already localized)
    val a11yDescription: String,
)

/** A styled polyline run in output px with per-vertex metric bucket (or -1). */
data class StyledRun(val pts: List<Pt>, val buckets: IntArray)

object Prepare {
    fun runs(points: List<TrackPoint>, privacy: PrivacySettings, spec: RenderSpec): List<StyledRun> {
        // Unwrap the WHOLE track before trimming: unwrapping each run separately puts runs on
        // opposite sides of ±180 and blows up the bbox. Haversine is unaffected by ±360 offsets.
        val unwrappedPos = WebMercator.unwrap(points.map { it.pos })
        val pairs = unwrappedPos.zip(points)
        val visiblePairs = trimForPrivacy(pairs, { it.first }, privacy)
        if (visiblePairs.isEmpty()) return emptyList()
        val visible = visiblePairs.map { r -> r.map { it.second } }
        val projected = visiblePairs.map { r -> r.map { WebMercator.project(it.first) } }
        val fitter = Fitter(bbox(projected), spec.traceRect, spec.padding)
        val eps = spec.toleranceOutputPx * fitter.metresPerPx
        val metric = metricOf(spec.style.colorBy)
        val all = visible.flatten().mapNotNull(metric)
        val (lo, hi) = percentiles(all, 0.05, 0.95)
        return projected.mapIndexed { ri, run ->
            val idx = when (spec.simplifier) {
                Simplifier.DOUGLAS_PEUCKER -> douglasPeucker(run, eps)
                Simplifier.VISVALINGAM -> visvalingam(run, eps * eps)
            }
            val src = visible[ri]
            StyledRun(
                pts = idx.map { fitter.map(run[it]) },
                buckets = IntArray(idx.size) { k -> metric(src[idx[k]])?.let { bucket(it, lo, hi, 32) } ?: -1 },
            )
        }
    }
    private fun metricOf(c: ColorBy): (TrackPoint) -> Double? = when (c) {
        ColorBy.SOLID -> { _ -> null }
        ColorBy.HR -> { p -> p.hr?.toDouble() }
        ColorBy.POWER -> { p -> p.power?.toDouble() }
        ColorBy.ELEVATION -> { p -> p.ele }
        ColorBy.PACE, ColorBy.SPEED -> { p -> p.distM?.let { d -> p.tSec?.let { t -> if (t > 0) d / t else null } } } // replace with smoothed instantaneous speed stream
    }
    private fun percentiles(v: List<Double>, a: Double, b: Double): Pair<Double, Double> {
        if (v.isEmpty()) return 0.0 to 1.0
        val s = v.sorted(); return s[(a * s.lastIndex).toInt()] to s[(b * s.lastIndex).toInt()]
    }
    private fun bucket(v: Double, lo: Double, hi: Double, n: Int) =
        (((v - lo) / maxOf(hi - lo, 1e-9)).coerceIn(0.0, 1.0) * (n - 1)).toInt()
}

object Palettes {
    // 32-step ramps generated from design tokens at build time; abbreviated here
    val PACE = IntArray(32) { lerpColor(0xFF2D7FF9.toInt(), 0xFFFF3B30.toInt(), it / 31f) }
    fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt() and 0xFF
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

object SvgBuilder {
    private fun f(v: Double) = String.format(Locale.ROOT, "%.2f", v)   // ROOT: never use locale decimal commas
    private fun hex(c: Int) = String.format(Locale.ROOT, "#%06X", c and 0xFFFFFF)
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun pathD(pts: List<Pt>, smooth: Boolean): String = buildString {
        if (pts.isEmpty()) return@buildString
        append("M").append(f(pts[0].x)).append(' ').append(f(pts[0].y))
        if (smooth && pts.size >= 3) catmullRomToBezier(pts).forEach { s ->
            append("C").append(f(s[1].x)).append(' ').append(f(s[1].y)).append(' ')
                .append(f(s[2].x)).append(' ').append(f(s[2].y)).append(' ')
                .append(f(s[3].x)).append(' ').append(f(s[3].y))
        } else for (i in 1 until pts.size) append("L").append(f(pts[i].x)).append(' ').append(f(pts[i].y))
    }

    fun build(runs: List<StyledRun>, spec: RenderSpec, fontCss: String = ""): String = buildString {
        val st = spec.style; val w = spec.widthPx; val h = spec.heightPx
        append("""<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" """)
        append("""viewBox="0 0 $w $h" width="$w" height="$h" role="img" aria-labelledby="t d">""")
        append("<title id=\"t\">").append(esc(spec.title ?: "Activity route")).append("</title>")
        append("<desc id=\"d\">").append(esc(spec.a11yDescription)).append("</desc>")
        append("<defs>")
        if (fontCss.isNotEmpty()) append("<style>").append(fontCss).append("</style>")
        if (st.glow) append("""<filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${f(st.strokePx * 0.8)}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>""")
        append("</defs>")
        if (!spec.bg.transparent) append("""<rect width="100%" height="100%" fill="${hex(spec.bg.color)}"/>""")
        spec.bg.basemapPngBase64?.let {
            val r = spec.traceRect
            append("""<image x="${f(r.minX)}" y="${f(r.minY)}" width="${f(r.w)}" height="${f(r.h)}" preserveAspectRatio="none" href="data:image/png;base64,$it" xlink:href="data:image/png;base64,$it"/>""") // xlink:href for SVG 1.1 renderers (AndroidSVG, older Inkscape)
        }
        append("""<g id="trace" fill="none" stroke-linecap="round" stroke-linejoin="round" stroke-width="${f(st.strokePx.toDouble())}"""")
        if (st.glow) append(""" filter="url(#glow)"""")
        append(">")
        for (run in runs) {
            if (st.colorBy == ColorBy.SOLID || run.buckets.all { it < 0 }) {
                append("""<path d="${pathD(run.pts, st.smooth)}" stroke="${hex(st.color)}"/>""")
            } else {
                // split into same-bucket sub-runs sharing endpoints (no gaps)
                var s = 0
                for (i in 1..run.pts.size) {
                    if (i == run.pts.size || run.buckets[i] != run.buckets[s]) {
                        val sub = run.pts.subList(s, minOf(i + 1, run.pts.size))
                        val b = run.buckets[s].coerceAtLeast(0)
                        if (sub.size >= 2) append("""<path d="${pathD(sub, st.smooth)}" stroke="${hex(st.palette[b])}"/>""")
                        s = i
                    }
                }
            }
        }
        append("</g>")
        if (st.startEndMarkers && runs.isNotEmpty()) {
            val a = runs.first().pts.first(); val z = runs.last().pts.last(); val r = st.strokePx * 1.4
            append("""<circle cx="${f(a.x)}" cy="${f(a.y)}" r="${f(r.toDouble())}" fill="#1DB954" stroke="#FFFFFF" stroke-width="${f(r / 3.0)}"><title>Start</title></circle>""")
            append("""<circle cx="${f(z.x)}" cy="${f(z.y)}" r="${f(r.toDouble())}" fill="#E5484D" stroke="#FFFFFF" stroke-width="${f(r / 3.0)}"><title>Finish</title></circle>""")
        }
        // stats block (template decides positions; simplified here: bottom row)
        if (spec.stats.isNotEmpty()) {
            val colW = w.toDouble() / spec.stats.size; val y = h - h * 0.06
            spec.stats.forEachIndexed { i, (label, value) ->
                val cx = colW * i + colW / 2
                append("""<text x="${f(cx)}" y="${f(y - 44)}" text-anchor="middle" class="stat-value">${esc(value)}</text>""")
                append("""<text x="${f(cx)}" y="${f(y)}" text-anchor="middle" class="stat-label">${esc(label)}</text>""")
            }
        }
        spec.attribution?.let {
            append("""<text x="${w - 12}" y="${h - 12}" text-anchor="end" class="attrib">${esc(it)}</text>""")
        }
        append("</svg>")
    }
}
```

Class styles (`stat-value`, `stat-label`, `attrib`) come from the `fontCss` emitted by the design-system token generator, e.g. `.stat-value{font:700 56px 'Inter Display';font-variant-numeric:tabular-nums;fill:#fff}`.

**Basemap guard:** `require(spec.bg.basemapPngBase64 == null || spec.attribution != null)`.

#### B.4.6 Android Canvas → Bitmap PNG

```kotlin
package app.stravafit.feature.share.render

import android.graphics.*
import androidx.core.graphics.withSave
import app.stravafit.core.geo.*   // Pt, catmullRomToBezier

object BitmapRenderer {
    fun render(runs: List<StyledRun>, spec: RenderSpec, typeface: Typeface, basemap: Bitmap? = null): Bitmap {
        val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (spec.bg.transparent) bmp.eraseColor(Color.TRANSPARENT) else c.drawColor(spec.bg.color)
        basemap?.let { val r = spec.traceRect
            c.drawBitmap(it, null, RectF(r.minX.toFloat(), r.minY.toFloat(), r.maxX.toFloat(), r.maxY.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG)) }
        val st = spec.style
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = st.strokePx
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        fun pathOf(pts: List<Pt>) = Path().apply {
            moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
            if (st.smooth && pts.size >= 3) catmullRomToBezier(pts).forEach { s ->
                cubicTo(s[1].x.toFloat(), s[1].y.toFloat(), s[2].x.toFloat(), s[2].y.toFloat(), s[3].x.toFloat(), s[3].y.toFloat()) }
            else for (i in 1 until pts.size) lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
        }
        c.withSave {
            if (st.glow) { // BlurMaskFilter needs software; we're drawing into a Bitmap canvas so OK
                // set color BEFORE alpha: Paint.setColor overwrites alpha
                val g = Paint(p).apply { maskFilter = BlurMaskFilter(st.strokePx * 1.6f, BlurMaskFilter.Blur.NORMAL); color = st.color; alpha = 160 }
                runs.forEach { r -> drawPath(pathOf(r.pts), g) }
            }
            for (run in runs) {
                if (st.colorBy == ColorBy.SOLID || run.buckets.all { it < 0 }) { p.color = st.color; drawPath(pathOf(run.pts), p) }
                else {
                    var s = 0
                    for (i in 1..run.pts.size) if (i == run.pts.size || run.buckets[i] != run.buckets[s]) {
                        val sub = run.pts.subList(s, minOf(i + 1, run.pts.size))
                        if (sub.size >= 2) { p.color = st.palette[run.buckets[s].coerceAtLeast(0)]; drawPath(pathOf(sub), p) }
                        s = i
                    }
                }
            }
        }
        if (st.startEndMarkers && runs.isNotEmpty()) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG); val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = st.strokePx * 0.45f }
            val r = st.strokePx * 1.4f
            val a = runs.first().pts.first(); val z = runs.last().pts.last()
            dot.color = 0xFF1DB954.toInt(); c.drawCircle(a.x.toFloat(), a.y.toFloat(), r, dot); c.drawCircle(a.x.toFloat(), a.y.toFloat(), r, ring)
            dot.color = 0xFFE5484D.toInt(); c.drawCircle(z.x.toFloat(), z.y.toFloat(), r, dot); c.drawCircle(z.x.toFloat(), z.y.toFloat(), r, ring)
        }
        val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { this.typeface = typeface; color = Color.WHITE; textAlign = Paint.Align.CENTER; fontFeatureSettings = "tnum" }
        if (spec.stats.isNotEmpty()) {
            val colW = spec.widthPx.toFloat() / spec.stats.size; val y = spec.heightPx * 0.94f
            spec.stats.forEachIndexed { i, (label, value) ->
                val cx = colW * i + colW / 2
                tp.textSize = spec.widthPx * 0.052f; c.drawText(value, cx, y - spec.widthPx * 0.04f, tp)
                tp.textSize = spec.widthPx * 0.022f; c.drawText(label.uppercase(), cx, y, tp)
            }
        }
        spec.attribution?.let { tp.textAlign = Paint.Align.RIGHT; tp.textSize = maxOf(18f, spec.widthPx * 0.012f); tp.alpha = 220
            c.drawText(it, spec.widthPx - 12f, spec.heightPx - 12f, tp) }
        return bmp
    }
}
```

PDF poster: `android.graphics.pdf.PdfDocument`. Create a page at a points size (A3 = 842×1191pt), then call the same draw routine on `page.canvas` with the scale applied. Vectors stay vectors in the PDF. The basemap stays raster at 300dpi.

#### B.4.7 Basemap snapshot (MapLibre)

```kotlin
// org.maplibre.gl:android-sdk
import android.content.Context
import android.graphics.Bitmap
import androidx.core.text.HtmlCompat
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter

fun snapshotBasemap(ctx: Context, styleUrl: String, bounds: LatLngBounds, wPx: Int, hPx: Int, padPx: Int, cb: (Bitmap, String) -> Unit) {
    val opts = MapSnapshotter.Options(wPx, hPx)
        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
        .withRegion(bounds)            // SAME trimmed bounds + padding as Fitter
        .withLogo(false)
        .withPixelRatio(1f)
    MapSnapshotter(ctx, opts).start({ snap ->
        // MapSnapshot.attributions is Array<String> of HTML snippets (no .title); strip tags
        val attr = snap.attributions.joinToString(" ") {
            HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        }
        cb(snap.bitmap, attr.ifBlank { "© OpenStreetMap contributors" })
    }, { err: String -> /* fall back to no basemap, show snackbar */ })
}
```

Match framing by computing the bounds from the **Fitter's** padded output rect, inverse-projected back to lat/lng. Then the raster and vector align to within 1px. A golden test covers this.

#### B.4.8 Sharing: FileProvider, generic intent, Instagram Stories

`AndroidManifest.xml`:
```xml
<provider android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.share" android:exported="false" android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/share_paths"/>
</provider>
<queries><package android:name="com.instagram.android"/></queries>
```
`res/xml/share_paths.xml`: `<paths><cache-path name="share" path="share/"/></paths>`

```kotlin
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Sharer {
    fun writeCache(ctx: Context, name: String, bytes: ByteArray): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000 }?.forEach { it.delete() }
        val f = File(dir, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)
    }

    fun shareGeneric(ctx: Context, uri: Uri, mime: String, text: String? = null) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri); text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            clipData = ClipData.newRawUri("", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, null))
    }

    /** Instagram Stories: sticker (transparent PNG) over a background image or top/bottom colors. */
    fun shareToInstagramStory(activity: Activity, stickerUri: Uri, bgUri: Uri?, topColor: String = "#101114", bottomColor: String = "#2D7FF9", fbAppId: String) {
        val i = Intent("com.instagram.share.ADD_TO_STORY").apply {
            putExtra("source_application", fbAppId)        // Meta now requires a Facebook App ID
            if (bgUri != null) setDataAndType(bgUri, "image/*") else type = "image/*"
            putExtra("interactive_asset_uri", stickerUri)
            putExtra("top_background_color", topColor); putExtra("bottom_background_color", bottomColor)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.grantUriPermission("com.instagram.android", stickerUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        bgUri?.let { activity.grantUriPermission("com.instagram.android", it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        // Extras verified against Meta "Sharing to Stories" (source_application required since Jan 2023;
        // sticker-only uses setType, background uses setDataAndType; colors default #222222)
        if (activity.packageManager.resolveActivity(i, 0) != null) activity.startActivity(i)
        else shareGeneric(activity, stickerUri, "image/png")   // fallback
    }
}
```
OSS note: `source_application` needs a Facebook App ID, which a fork must register itself. Read it from `BuildConfig`, and if it's empty, hide the IG button and fall back to the system share.

Clipboard sticker (Strava's "copy sticker"): `ClipboardManager.setPrimaryClip(ClipData.newUri(contentResolver, "Route sticker", uri))`. Android 13+ shows its own confirmation, so don't show a duplicate toast.

#### B.4.9 Save via MediaStore

```kotlin
fun saveToGallery(ctx: Context, name: String, mime: String, bytes: ByteArray): Uri? {
    val collection = when {
        mime.startsWith("image/") && mime != "image/svg+xml" -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)   // SVG, PDF, GPX, FIT…
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (mime.startsWith("image/") && mime != "image/svg+xml") "Pictures/StravaFit" else "Download/StravaFit")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val r = ctx.contentResolver; val uri = r.insert(collection, values) ?: return null
    try { r.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream") }
    catch (e: Exception) { r.delete(uri, null, null); return null }   // don't leave a pending orphan
    values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); r.update(uri, values, null, null)
    return uri
}
```
minSdk ≥ 29 for this path. API 26-28: fall back to `ACTION_CREATE_DOCUMENT` (SAF), which avoids needing WRITE_EXTERNAL_STORAGE. SAF ("Save to…") is also offered on every API level for picking a custom location.

#### B.4.10 Data exports (GPX / TCX / GeoJSON / FIT)

```kotlin
import app.stravafit.core.geo.TrackPoint
import java.util.Locale

object GpxWriter {
    fun write(name: String, sport: String, pts: List<TrackPoint>, startEpochSec: Long): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="StravaFit" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""")
        append("<metadata><name>").append(xmlEsc(name)).append("</name><time>").append(iso(startEpochSec)).append("</time></metadata>")
        append("<trk><name>").append(xmlEsc(name)).append("</name><type>").append(xmlEsc(sport)).append("</type><trkseg>")
        for (p in pts) {
            append("""<trkpt lat="${"%.7f".format(Locale.ROOT, p.pos.lat)}" lon="${"%.7f".format(Locale.ROOT, p.pos.lng)}">""")
            p.ele?.let { append("<ele>").append("%.1f".format(Locale.ROOT, it)).append("</ele>") }
            p.tSec?.let { append("<time>").append(iso(startEpochSec + it.toLong())).append("</time>") }
            if (p.hr != null) append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>${p.hr}</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
            append("</trkpt>")
        }
        append("</trkseg></trk></gpx>")
    }
    private fun iso(s: Long) = java.time.Instant.ofEpochSecond(s).toString()
    private fun xmlEsc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
```
- **TCX:** `TrainingCenterDatabase` → `Activity Sport=` → `Lap` (per auto-lap) → `Track/Trackpoint` with `DistanceMeters`, `HeartRateBpm`, `Extensions/TPX/Watts`.
- **GeoJSON:** a `FeatureCollection` with a `LineString` (or a `MultiLineString` after privacy trimming), `[lng, lat, ele]`, and properties `{name, sport, start, distance_m, moving_time_s}`. Optionally one Point feature per km marker.
- **FIT:** use the Garmin FIT SDK (Java, open licence; check its redistribution terms) or `fit4j`-style encoder. Messages: FileId, Event, Record, Lap, Session, Activity.
- **Original:** byte-for-byte copy of the imported file.
- **Privacy for data exports:** a "Apply privacy zones" toggle, **ON by default** for any share target, **OFF by default** for "Back up to my device". A warning appears when you turn it off for sharing.
- **Bulk export:** Settings → Data → "Export everything" runs a WorkManager job that streams to a zip through SAF: `activities/{yyyy-mm-dd}_{id}.{gpx|fit|orig}`, `activities.csv`, `routes/*.gpx`, `gear.csv`, `profile.json`, `media/`, plus a `README.txt` describing the schema. Progress notification, resumable.

#### B.4.11 Compose export screen (preview)

```kotlin
@Composable
fun ExportScreen(state: ExportUiState, on: (ExportEvent) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Share & export") },
        navigationIcon = { IconButton({ on(ExportEvent.Close) }) { Icon(Icons.Default.Close, "Close") } }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            TemplatePager(state.templates, state.selected, state.preview, onSelect = { on(ExportEvent.Template(it)) })
            when (val s = state.status) {   // bind: smart cast on state.status fails across modules
                is Status.Rendering -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is Status.Error -> ErrorBanner(s.msg) { on(ExportEvent.Retry) }
                else -> {}
            }
            SectionHeader("Style")
            ChipRow(ColorBy.entries, state.colorBy, label = { it.label() }, enabled = { state.available(it) }) { on(ExportEvent.ColorBy(it)) }
            ToggleRow("Basemap", state.basemap) { on(ExportEvent.Basemap(it)) }
            ToggleRow("Smooth line", state.smooth) { on(ExportEvent.Smooth(it)) }
            ToggleRow("Glow", state.glow) { on(ExportEvent.Glow(it)) }
            ToggleRow("Start/finish markers", state.markers) { on(ExportEvent.Markers(it)) }
            ToggleRow("Distance markers", state.kmMarkers) { on(ExportEvent.KmMarkers(it)) }
            BackgroundPicker(state.bg) { on(ExportEvent.Bg(it)) }
            SectionHeader("Stats (${state.stats.count { it.on }}/4)")
            StatPicker(state.stats, max = 4) { on(ExportEvent.ToggleStat(it)) }
            PrivacyRow(state.privacyApplied, state.hiddenMeters)    // read-only summary + link to settings
            SectionHeader("Share to")
            TargetGrid(state.targets) { on(ExportEvent.Share(it)) } // IG Story, IG Post, WhatsApp, Copy sticker, Copy link, More
            SectionHeader("Export file")
            FormatGrid(listOf("SVG","PNG","PNG (transparent)","PDF poster","GPX","TCX","FIT","GeoJSON","Original")) { on(ExportEvent.Export(it)) }
        }
    }
}

@Preview(name = "Story template", widthDp = 360, heightDp = 800)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", fontScale = 2f)
@Composable private fun ExportScreenPreview() = StravaFitTheme { ExportScreen(ExportUiState.sample(), {}) }
```
Render runs in a `ViewModel` on `Dispatchers.Default`, debounced 150ms after a style change. The preview is drawn at 0.5× resolution, and the full resolution only on export. Cancel the in-flight job when settings change.

### B.5 JS/TS version (web, same output)

```ts
// packages/route-art/src/index.ts — zero deps, runs in browser, Node, Deno
export type LatLng = [lat: number, lng: number];
export interface Pt { x: number; y: number }

export function decodePolyline(s: string, precision = 5): LatLng[] {
  const f = 10 ** precision, out: LatLng[] = [];
  let i = 0, lat = 0, lng = 0;
  const next = () => { let r = 0, sh = 0, b: number;
    do { b = s.charCodeAt(i++) - 63; r |= (b & 0x1f) << sh; sh += 5; } while (b >= 0x20);
    return r & 1 ? ~(r >> 1) : r >> 1; };
  while (i < s.length) { lat += next(); lng += next(); out.push([lat / f, lng / f]); }
  return out;
}

const R = 6378137, MAXLAT = 85.05112878;
export const project = ([lat, lng]: LatLng): Pt => {
  const la = Math.max(-MAXLAT, Math.min(MAXLAT, lat)) * Math.PI / 180;
  return { x: R * lng * Math.PI / 180, y: R * Math.log(Math.tan(Math.PI / 4 + la / 2)) };
};

/** Same as Kotlin WebMercator.unwrap: keep tracks crossing ±180 contiguous. */
export function unwrap(pts: LatLng[]): LatLng[] {
  let off = 0, prev = pts.length ? pts[0][1] : 0;
  return pts.map(([la, lo]) => { const d = lo - prev; if (d > 180) off -= 360; else if (d < -180) off += 360; prev = lo; return [la, lo + off] as LatLng; });
}

export function haversine(a: LatLng, b: LatLng) {
  const r = 6371008.8, rad = Math.PI / 180, dLa = (b[0] - a[0]) * rad, dLo = (b[1] - a[1]) * rad;
  const h = Math.sin(dLa / 2) ** 2 + Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin(dLo / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
}

export interface Privacy { hideStartEndM?: number; zones?: { c: LatLng; r: number }[]; hideMap?: boolean }
export function trim(pts: LatLng[], p: Privacy): LatLng[][] {
  if (p.hideMap || pts.length < 2) return [];
  const s = pts[0], e = pts[pts.length - 1], R0 = p.hideStartEndM ?? 0;
  const hidden = (q: LatLng) => (R0 > 0 && (haversine(q, s) <= R0 || haversine(q, e) <= R0)) || (p.zones ?? []).some(z => haversine(q, z.c) <= z.r);
  const runs: LatLng[][] = []; let cur: LatLng[] | null = null;
  for (const q of pts) { if (hidden(q)) cur = null; else { if (!cur) runs.push(cur = []); cur.push(q); } }
  return runs.filter(r => r.length >= 2);
}

export function douglasPeucker(p: Pt[], eps: number): Pt[] {
  if (p.length < 3) return p.slice();
  const keep = new Uint8Array(p.length); keep[0] = keep[p.length - 1] = 1;
  const st: [number, number][] = [[0, p.length - 1]], e2 = eps * eps;
  while (st.length) { const [a, b] = st.pop()!; let m = -1, idx = -1;
    for (let i = a + 1; i < b; i++) { const d = segD2(p[i], p[a], p[b]); if (d > m) { m = d; idx = i; } }
    if (m > e2) { keep[idx] = 1; st.push([a, idx], [idx, b]); } }
  return p.filter((_, i) => keep[i]);
}
function segD2(q: Pt, a: Pt, b: Pt) { const dx = b.x - a.x, dy = b.y - a.y;
  const t = dx || dy ? Math.max(0, Math.min(1, ((q.x - a.x) * dx + (q.y - a.y) * dy) / (dx * dx + dy * dy))) : 0;
  return (q.x - a.x - t * dx) ** 2 + (q.y - a.y - t * dy) ** 2; }

export interface ArtOptions {
  width: number; height: number; padding?: number; stroke?: string; strokeWidth?: number;
  background?: string | null; smooth?: boolean; glow?: boolean; markers?: boolean;
  privacy?: Privacy; title?: string; desc: string; tolerancePx?: number;
}

const n = (v: number) => v.toFixed(2);
const esc = (s: string) => s.replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!));

function d(p: Pt[], smooth: boolean) {
  let s = `M${n(p[0].x)} ${n(p[0].y)}`;
  if (!smooth || p.length < 3) { for (let i = 1; i < p.length; i++) s += `L${n(p[i].x)} ${n(p[i].y)}`; return s; }
  for (let i = 0; i < p.length - 1; i++) {
    const p0 = p[Math.max(i - 1, 0)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(i + 2, p.length - 1)];
    s += `C${n(p1.x + (p2.x - p0.x) / 6)} ${n(p1.y + (p2.y - p0.y) / 6)} ${n(p2.x - (p3.x - p1.x) / 6)} ${n(p2.y - (p3.y - p1.y) / 6)} ${n(p2.x)} ${n(p2.y)}`;
  }
  return s;
}

export function routeSvg(latlngs: LatLng[], o: ArtOptions): string {
  const { width: W, height: H, padding = 0.08, stroke = '#FC5200', strokeWidth = Math.max(4, 0.006 * Math.min(W, H)) } = o;
  const runs = trim(unwrap(latlngs), o.privacy ?? {}).map(r => r.map(project));
  if (!runs.length) return ''; // caller shows stats-only card
  let [x0, y0, x1, y1] = [Infinity, Infinity, -Infinity, -Infinity];
  runs.flat().forEach(p => { x0 = Math.min(x0, p.x); y0 = Math.min(y0, p.y); x1 = Math.max(x1, p.x); y1 = Math.max(y1, p.y); });
  const px = W * padding, py = H * padding;
  const k = Math.min((W - 2 * px) / Math.max(x1 - x0, 1e-9), (H - 2 * py) / Math.max(y1 - y0, 1e-9));
  const ox = (W - (x1 - x0) * k) / 2 - x0 * k, oy = (H - (y1 - y0) * k) / 2 + y1 * k;
  const eps = (o.tolerancePx ?? 0.5) / k;
  const out = runs.map(r => douglasPeucker(r, eps).map(p => ({ x: ox + p.x * k, y: oy - p.y * k })));
  const a = out[0][0], z = out[out.length - 1].at(-1)!, mr = strokeWidth * 1.4;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-labelledby="t d">` +
    `<title id="t">${esc(o.title ?? 'Activity route')}</title><desc id="d">${esc(o.desc)}</desc>` +
    (o.glow ? `<defs><filter id="glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="${n(strokeWidth * .8)}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>` : '') +
    (o.background ? `<rect width="100%" height="100%" fill="${o.background}"/>` : '') +
    `<g fill="none" stroke="${stroke}" stroke-width="${n(strokeWidth)}" stroke-linecap="round" stroke-linejoin="round"${o.glow ? ' filter="url(#glow)"' : ''}>` +
    out.map(r => `<path d="${d(r, !!o.smooth)}"/>`).join('') + `</g>` +
    (o.markers !== false ? `<circle cx="${n(a.x)}" cy="${n(a.y)}" r="${n(mr)}" fill="#1DB954" stroke="#fff" stroke-width="${n(mr / 3)}"/><circle cx="${n(z.x)}" cy="${n(z.y)}" r="${n(mr)}" fill="#E5484D" stroke="#fff" stroke-width="${n(mr / 3)}"/>` : '') +
    `</svg>`;
}

/** Browser PNG: rasterize SVG via OffscreenCanvas (no taint since it's a data/blob URL without external refs). */
export async function svgToPng(svg: string, w: number, h: number): Promise<Blob> {
  const img = new Image(); img.src = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
  await img.decode(); const c = new OffscreenCanvas(w, h); c.getContext('2d')!.drawImage(img, 0, 0, w, h);
  URL.revokeObjectURL(img.src); return c.convertToBlob({ type: 'image/png' });
}
/** Web share with file fallback to download */
export async function shareFile(blob: Blob, name: string) {
  const file = new File([blob], name, { type: blob.type });
  if (navigator.canShare?.({ files: [file] })) return navigator.share({ files: [file], title: name });
  const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: name }); a.click();
}
```
**Parity contract:** Kotlin and TS must produce byte-identical SVG for the same fixture, with both formatting numbers to 2 decimals in the ROOT/en locale. Note: the reference snippets above are NOT yet byte-identical (Kotlin emits `<defs>` always, `xmlns:xlink`, `id="trace"`, per-path `stroke`, `<title>` inside markers; TS puts `stroke` on the group). Align them before recording goldens. Also clamp `-0.00` to `0.00` and avoid exact .xx5 ties (Java HALF_UP vs JS `toFixed` differ on negative ties). Shared golden fixtures live in `/fixtures/route-art/*.json` and `*.svg`.

### B.6 Export screen wireframes (all states)

**Default (Story template, private activity OK)**
```
+--------------------------------------+
| X        Share & export        [i]   |
|--------------------------------------|
| [Story][Square][Wide][Poster][Stick] |
|  +--------------------------+        |
|  |      ~~~/\_/~~~\         |        |
|  |     /          \_o       |        |
|  |  *_/   (trace preview)   |        |
|  |  10.2 km  52:40  5:10/km |        |
|  +--------------------------+        |
|         o  *  o  o  o               |
|--------------------------------------|
| Color by: (Solid)(Pace)(HR)(Elev)(W) |
| Basemap            [ off ]           |
| Smooth line        [ on  ]           |
| Glow               [ off ]           |
| Start/finish       [ on  ]           |
| Km markers         [ off ]           |
| Background: (None)(Dark)(Light)(Photo)|
| Stats 3/4: [x]Dist [x]Time [x]Pace   |
|            [ ]Elev [ ]HR  [ ]Cal     |
| Privacy: 200 m hidden at start/end > |
|--------------------------------------|
| SHARE TO                             |
| [IG Story][IG Post][WhatsApp][Copy]  |
| [Copy link][More...]                 |
| EXPORT FILE                          |
| [SVG][PNG][PNG-T][PDF][GPX][TCX]     |
| [FIT][GeoJSON][Original]             |
+--------------------------------------+
```

**Rendering**
```
+--------------------------------------+
| X        Share & export              |
|======================== 60% ======== |
|  +--------------------------+        |
|  |   (previous preview,     |        |
|  |    dimmed 50%)           |        |
|  +--------------------------+        |
| Share buttons disabled until ready   |
+--------------------------------------+
```

**Map hidden / no GPS (treadmill, indoor)**
```
+--------------------------------------+
|  +--------------------------+        |
|  |   [no-route icon]        |        |
|  |   Indoor run             |        |
|  |   10.0 km  50:00  5:00   |        |
|  +--------------------------+        |
| Route art unavailable: map hidden    |
| or no GPS. Stats card only.          |
| [SVG/PNG ok] [GPX disabled -> why?]  |
+--------------------------------------+
```

**Privacy trimmed everything** (short activity inside zone)
```
| ! Your whole route is inside a       |
|   privacy zone. Sharing stats only.  |
|   [Edit privacy zones]               |
```

**Metric unavailable** (no HR stream)
```
| Color by: (Solid)(Pace)(HR-disabled) |
| "No heart-rate data for this run"    |
```

**Error**
```
| x Couldn't render basemap (offline). |
|   [Retry] [Continue without map]     |
```

**Success (saved)**
```
| v Saved to Pictures/StravaFit        |
|   [Open] [Share]          (snackbar) |
```

**Instagram not installed** → the IG buttons are hidden and the "More…" chooser is used instead.
**Bulk export in progress** (Settings): notification "Exporting 1,284 activities… 37%" [Cancel].

### B.7 Accessibility

- Every exported SVG has `role="img"`, `<title>`, and a `<desc>` like "Run route, 10.2 km loop starting and ending in the same area, 52 minutes 40 seconds, average pace 5:10 per km". It is generated from stats, **never from location names inside privacy zones**.
- PNG share intent: `EXTRA_TEXT` carries the same alt text so the user can paste it into IG alt-text and X image descriptions.
- Gradient modes never rely on color alone. The legend shows labeled min/max values, and HR zones use a colorblind-safe palette (validated against deuteranopia and protanopia simulation in tests).
- Compose: all chips and toggles get `Modifier.semantics { stateDescription }`. The preview has `contentDescription` set to the same alt text. Touch targets are ≥ 48dp. The screen supports 200% font scale (the preview shrinks, controls wrap). TalkBack announces "Rendering…" and "Ready" through a live region.
- Reduced motion: no pager animations when the system "remove animations" setting is on.
- Minimum text contrast inside images is 4.5:1 against the chosen background. The renderer auto-adds a scrim behind stats on photo backgrounds.

### B.8 File naming

Pattern: `stravafit_{yyyy-MM-dd}_{sport}_{slug(name, 40)}_{template}.{ext}`
e.g. `stravafit_2026-10-29_run_morning-loop_story.png`, `stravafit_2026-10-29_run_morning-loop.gpx`.
- The slug uses ASCII-folded lowercase with `-` and drops `/\:*?"<>|`. If the result is empty, use the activity ID.
- Collisions: MediaStore auto-suffixes and SAF follows the user's choice.
- Bulk export: `activities/{yyyy-MM-dd}_{activityId}.{ext}` (sortable, unique).
- Never put coordinates or a home-town name in the filename.

### B.9 Size limits & performance

| Output | Limit / target |
|---|---|
| Story PNG | 1080×1920, ≤ 2 MB (IG recompresses; PNG-8 quantize if over) |
| Sticker PNG | ≤ 1080 px long side, ≤ 1 MB (IG sticker sweet spot, unverified) |
| SVG single route | ≤ 200 KB target. Path vertex cap of 5,000 per output (raise eps until met) |
| SVG with basemap | Basemap embedded as JPEG/PNG ≤ 2 MB, or a "pure vector" toggle |
| Heatmap SVG | ≤ 2,000 activities, else PNG only. Warn above 10 MB |
| PDF poster | A2 at 300dpi raster parts ≤ 25 MB |
| Bitmap memory | Guard: `w*h*4 ≤ 0.25 * maxMemory`. Otherwise tile-render (poster) or downscale |
| Render time | ≤ 150 ms preview, ≤ 800 ms full story on a mid-range device (Pixel 6a) |
| GPX for 10h ride at 1 Hz | ~36k points, ~4 MB. Stream the write and don't build the full string in memory for >50k points |

### B.10 Tests

- **Unit (JVM, `:core:geo`):** polyline round-trip (Google reference vectors such as `_p~iF~ps|U_ulLnnqC_mqNvxq`@`), projection at the equator and poles, anti-meridian unwrap, DP/VW keep the endpoints, the tolerance monotonicity property, and privacy trim (no output point lies within R of any hidden center, checked as a property test with random tracks via kotest-property).
- **Golden SVG snapshots:** `fixtures/route-art/{loop,out-and-back,antimeridian,zone-mid-track,indoor,huge-ride}.json` × templates × colorBy produce `*.svg` committed files. The test regenerates and diffs byte-for-byte. `./gradlew :feature:share:recordGoldens` updates them. The same fixtures run in a TS Vitest suite, which asserts parity with the Kotlin output.
- **Raster goldens:** Paparazzi (or Roborazzi) screenshots of the Compose ExportScreen in each state, plus `BitmapRenderer` output compared with a 0.5% pixel-diff tolerance.
- **Basemap alignment:** render a known square route over a synthetic style with a marker at the trace's start and assert ≤ 1 px offset.
- **Format validation:** GPX validated against the GPX 1.1 XSD, TCX against the TCX v2 XSD, GeoJSON against RFC 7946 (right-hand rule n/a for lines), and FIT decoded back with the FIT SDK with the record count asserted.
- **Privacy regression:** for every share path (SVG, PNG, PDF, GPX-share, OG image), assert no coordinate within the zone appears. For raster outputs, check by inverse-projecting nonzero alpha pixels.
- **Intent tests:** Robolectric checks that the IG intent has `interactive_asset_uri`, the grant flags, and the fallback when the package is missing.
- **Accessibility:** the Compose a11y checks (`ATF`) on all states, plus a font scale 2.0 snapshot.

---

## OSS implementation notes

- **Module layout:** `:core:geo` (pure Kotlin, multiplatform-ready: decode, projection, simplify, trim, SVG builder), `:feature:share` (Android Canvas, PDF, MapLibre, intents, Compose UI), `packages/route-art` (TS, npm-publishable, MIT). Keeping the geometry in pure Kotlin lets a later Kotlin Multiplatform JS target replace the hand-ported TS. Until then, golden parity tests keep the two in sync.
- **Licences:** MapLibre Native (BSD-2) is fine. OSM data is ODbL, so attribution is mandatory on any image with a basemap. Choose tile hosting that allows static rendering (OpenFreeMap, a self-hosted PMTiles/Protomaps build, or a MapTiler key supplied by the user). Never default to tile.openstreetmap.org. Garmin FIT SDK: check its licence before vendoring. Optionally use a clean-room FIT encoder.
- **Instagram Stories** needs a Meta/Facebook App ID in `source_application`. Make it a build config value (`STRAVAFIT_FB_APP_ID`). F-Droid builds leave it empty, which hides the direct IG button.
- **No Strava assets:** don't use Strava orange as our brand default. Use our design-system `route.primary` token (the orange in code samples is a placeholder). No Strava logos or wording in templates.
- **Privacy first:** the renderer API takes `PrivacySettings` as a **required** parameter, with no default overload, so a caller can't forget to trim. Only `DataExport.backup()` accepts `PrivacySettings.NONE`, and it is lint-annotated `@RawLocationExport`.
- **Fonts in SVG:** subset with `fonttools pyftsubset` at build time for digits + Latin basic. Offer "Outline text" for non-Latin names (convert text to paths with `android.graphics.Paint.getTextPath` → SVG path data).
- **Link sharing / OG images (P2):** a self-hosted instance renders OG images server-side with the same TS package (Node + resvg-js for PNG). Private activities never get public URLs.
- **Performance:** cache the projected and simplified geometry per (activityId, privacyHash, outputSize bucket). Style changes reuse the geometry and only re-emit.
- **Strava import:** reading a Strava bulk archive (`activities/*.fit(.gz)`, `.gpx(.gz)`, `activities.csv`) is the onboarding path. Share the parser with B.4.10.
- **Open questions:** whether to support Strava-style "ENDS_ONLY" trimming as the default (closer parity) or the stricter all-pass trimming (safer). The current proposal is to default to the stricter mode and offer the other as an option. Also whether to support animated route MP4/GIF for Stories (P2 idea, Lottie-free, via MediaCodec frame encode).

## Sources

- BikeRadar: Strava "Sticker Stats" spring 2025 update (sticker contents, copy to clipboard, IG Stories, visibility requirement). https://www.bikeradar.com/news/strava-sticker-stats-spring-2025-updates
- remove.bg blog: Strava overlay for Instagram Stories (third-party description of the IG sticker flow and the transparent-overlay workaround). https://www.remove.bg/b/strava-overlay-for-instagram-stories-and-posts
- DC Rainmaker: Strava privacy features and options (2021). https://dcrainmaker.com/2021/08/privacy-features-options.html
- the5krunner: New Strava privacy zone map changes. https://the5krunner.com/2021/08/26/new-strava-privacy-zone-map-changes-quite-nice/
- Velo / Outside: Strava activity and privacy updates (hide start/end up to 1 mi, hide map). https://velo.outsideonline.com/2021/08/strava-rolls-out-activity-and-privacy-updates-for-subscribers-and-free-users/
- Zwift Insider: Strava map visibility (segment matching in hidden zones). https://zwiftinsider.com/strava-map-visibility/
- Tom's Guide: Strava privacy setting guidance. https://www.tomsguide.com/uk/how-to/this-is-the-one-strava-setting-you-need-to-change-now
- flopp/StravaExportToGPX: bulk export steps (Download or Delete Your Account → Request archive). https://github.com/flopp/StravaExportToGPX
- fit2gpx docs: Strava archive contents in original format (FIT/GPX, gzipped, named by ID). https://fit2gpx.readthedocs.io/en/stable/
- Strava Bulk GPX Export (third-party Chrome extension, GPX/TCX/original). https://chromewebstore.google.com/detail/nabkcpkedegngoebodobopcojpahgbba
- Strava Developers API reference (streams, route export_gpx/export_tcx). https://developers.strava.com/docs/reference/
- Strava Support (help center, export and privacy articles; check the current wording). https://support.strava.com/
- Strava Support: Flyover (subscriber 3D replay, share to IG Stories or via "More"). https://support.strava.com/hc/en-us/articles/19900004650125-Flyover · GCN launch coverage https://www.globalcyclingnetwork.com/tech/news/strava-launches-new-flyover-feature-for-3d-post-ride-video-recap · road.cc https://www.road.cc/content/tech-news/strava-introduces-flyover-3d-video-maps-305103
- Meta for Developers: Sharing to Instagram Stories (Android intent extras, re-checked this pass). https://developers.facebook.com/docs/instagram-platform/sharing-to-stories/
- Android Developers: FileProvider, MediaStore, PdfDocument. https://developer.android.com/training/secure-file-sharing , https://developer.android.com/training/data-storage/shared/media
- MapLibre Native Android: MapSnapshotter. https://maplibre.org/maplibre-native/android/api/
- OpenStreetMap copyright and attribution; tile usage policy. https://www.openstreetmap.org/copyright , https://operations.osmfoundation.org/policies/tiles/
- Google encoded polyline algorithm. https://developers.google.com/maps/documentation/utilities/polylinealgorithm
- GPX 1.1 schema. https://www.topografix.com/GPX/1/1/ · GeoJSON RFC 7946. https://datatracker.ietf.org/doc/html/rfc7946

> Research note: this pass was time-boxed at the user's request. Strava UI labels in A.3-A.6 marked (unverified) come from secondary sources and should be checked against the live app and support.strava.com in a follow-up sweep.
