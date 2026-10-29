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
}
