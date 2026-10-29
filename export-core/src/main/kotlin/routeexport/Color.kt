package routeexport

import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** sRGB colour with channels in [0,1]. */
data class Rgb(val r: Double, val g: Double, val b: Double) {
    fun hex(): String = "#%02X%02X%02X".format(ch(r), ch(g), ch(b))

    private fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()

    companion object {
        fun parse(hex: String): Rgb {
            val h = hex.removePrefix("#")
            require(h.length == 6) { "Expected #RRGGBB, got $hex" }
            return Rgb(
                h.substring(0, 2).toInt(16) / 255.0,
                h.substring(2, 4).toInt(16) / 255.0,
                h.substring(4, 6).toInt(16) / 255.0,
            )
        }
    }
}

/** OKLab (Björn Ottosson) — perceptually even interpolation, no muddy midpoints. */
internal data class Lab(val l: Double, val a: Double, val b: Double)

internal object OkLab {
    private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun toGamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

    fun fromRgb(c: Rgb): Lab {
        val r = toLinear(c.r)
        val g = toLinear(c.g)
        val b = toLinear(c.b)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Lab(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    fun toRgb(c: Lab): Rgb {
        val l = (c.l + 0.3963377774 * c.a + 0.2158037573 * c.b).pow(3)
        val m = (c.l - 0.1055613458 * c.a - 0.0638541728 * c.b).pow(3)
        val s = (c.l - 0.0894841775 * c.a - 1.2914855480 * c.b).pow(3)
        return Rgb(
            toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        )
    }
}

/** Multi-stop colour ramp; t=0 is the low end (slow / low HR / low elevation). */
class Ramp(val name: String, stops: List<String>) {
    private val labs = stops.map { OkLab.fromRgb(Rgb.parse(it)) }
    val stops: List<String> = stops

    init {
        require(stops.size >= 2) { "Ramp needs at least 2 stops" }
    }

    fun at(t: Double): String {
        val x = t.coerceIn(0.0, 1.0) * (labs.size - 1)
        val i = floor(x).toInt().coerceAtMost(labs.size - 2)
        val f = x - i
        val a = labs[i]
        val b = labs[i + 1]
        return OkLab.toRgb(Lab(a.l + (b.l - a.l) * f, a.a + (b.a - a.a) * f, a.b + (b.b - a.b) * f)).hex()
    }
}

object Ramps {
    val AURORA = Ramp("aurora", listOf("#2DD4BF", "#38BDF8", "#818CF8", "#E879F9", "#FB7185"))
    val SUNSET = Ramp("sunset", listOf("#FDE68A", "#FDBA74", "#FB7185", "#C084FC"))
    val GLACIER = Ramp("glacier", listOf("#67E8F9", "#38BDF8", "#2563EB", "#312E81"))
    val MEADOW = Ramp("meadow", listOf("#D9F99D", "#86EFAC", "#2DD4BF", "#0EA5E9"))
    val INK = Ramp("ink", listOf("#94A3B8", "#475569", "#0F172A"))
}
