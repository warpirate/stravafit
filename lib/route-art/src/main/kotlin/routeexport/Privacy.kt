package routeexport

object Privacy {
    /**
     * Hides the area around the start and end: drops every point within [radiusM] of either.
     * Apply BEFORE projection so the export's framing does not leak the hidden area either.
     * Returns an empty list if nothing remains.
     */
    fun trim(points: List<TrackPoint>, radiusM: Double): List<TrackPoint> {
        if (radiusM <= 0.0 || points.isEmpty()) return points
        val start = points.first()
        val end = points.last()
        return points.filter { Geo.haversine(it, start) >= radiusM && Geo.haversine(it, end) >= radiusM }
    }

    /**
     * Like [trim], with its own radius for the start and the end (the owner's "hide start" and "hide end"
     * settings). Fewer than two points left means nothing may be drawn: the result is then empty.
     */
    fun trimEnds(points: List<TrackPoint>, startM: Double, endM: Double): List<TrackPoint> {
        if (points.size < 2) return emptyList()
        val start = points.first()
        val end = points.last()
        val kept = points.filter {
            (startM <= 0.0 || Geo.haversine(it, start) >= startM) && (endM <= 0.0 || Geo.haversine(it, end) >= endM)
        }
        return if (kept.size >= 2) kept else emptyList()
    }
}
