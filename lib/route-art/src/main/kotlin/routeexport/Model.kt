package routeexport

/** One GPS sample. [time] is seconds since activity start (or epoch); only differences matter. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Double? = null,
    val hr: Int? = null,
    val power: Int? = null,
)

enum class Sport(val label: String, val showsPace: Boolean) {
    RUN("Run", true),
    WALK("Walk", true),
    HIKE("Hike", true),
    RIDE("Ride", false),
    SWIM("Swim", true),
    OTHER("Activity", false),
}

/**
 * @property summary stats to print on cards instead of the ones computed from [points]. Callers that
 *   privacy-trim the points pass the full activity's stats here, so only the map is trimmed.
 */
data class Track(
    val points: List<TrackPoint>,
    val title: String = "",
    val subtitle: String = "",
    val sport: Sport = Sport.RUN,
    val summary: Stats? = null,
)

enum class Units { METRIC, IMPERIAL }

/** What drives the route colour. Falls back to [PROGRESS] when the data is missing. */
enum class ColorBy { SOLID, PROGRESS, PACE, HEART_RATE, ELEVATION, POWER }
