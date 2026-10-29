package routeexport

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Format {
    private const val M_PER_MI = 1609.344
    private const val FT_PER_M = 3.28084

    fun distance(m: Double, units: Units): Pair<String, String> {
        val (v, unit) = if (units == Units.METRIC) m / 1000 to "km" else m / M_PER_MI to "mi"
        val s = if (v >= 100) String.format(Locale.ROOT, "%.1f", v) else String.format(Locale.ROOT, "%.2f", v)
        return s to unit
    }

    fun duration(sec: Double): String {
        val t = sec.roundToLong()
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun pace(movingSec: Double, distanceM: Double, units: Units): Pair<String, String> {
        val per = if (units == Units.METRIC) 1000.0 else M_PER_MI
        val t = (movingSec / (distanceM / per)).roundToLong()
        return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60) to if (units == Units.METRIC) "/km" else "/mi"
    }

    fun speed(movingSec: Double, distanceM: Double, units: Units): Pair<String, String> {
        val mps = distanceM / movingSec
        val v = if (units == Units.METRIC) mps * 3.6 else mps * 3600 / M_PER_MI
        return String.format(Locale.ROOT, "%.1f", v) to if (units == Units.METRIC) "km/h" else "mph"
    }

    fun elevation(m: Double, units: Units): Pair<String, String> =
        if (units == Units.METRIC) m.roundToInt().toString() to "m"
        else (m * FT_PER_M).roundToInt().toString() to "ft"
}
