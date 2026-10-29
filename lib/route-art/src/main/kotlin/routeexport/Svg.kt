package routeexport

import java.util.Locale

/** Compact number formatting for SVG: max 2 decimals, no trailing zeros, Locale-independent. */
internal fun Double.f(): String {
    if (isNaN() || isInfinite()) return "0"
    val s = String.format(Locale.ROOT, "%.2f", this).trimEnd('0').trimEnd('.')
    return if (s == "-0") "0" else s
}

/** Minimal SVG writer. Attributes with null values are omitted. */
class Svg(val width: Int, val height: Int) {
    private val defs = StringBuilder()
    private val body = StringBuilder()
    private var idSeq = 0

    fun newId(prefix: String) = "$prefix${++idSeq}"

    fun def(raw: String) {
        defs.append(raw).append('\n')
    }

    fun raw(s: String) {
        body.append(s).append('\n')
    }

    fun el(name: String, vararg attrs: Pair<String, Any?>, content: String? = null) {
        body.append('<').append(name).append(attrString(attrs))
        if (content == null) body.append("/>\n")
        else body.append('>').append(esc(content)).append("</").append(name).append(">\n")
    }

    fun group(vararg attrs: Pair<String, Any?>, block: () -> Unit) {
        body.append("<g").append(attrString(attrs)).append(">\n")
        block()
        body.append("</g>\n")
    }

    fun build(title: String? = null): String = buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">\n")
        if (!title.isNullOrBlank()) append("<title>").append(esc(title)).append("</title>\n")
        if (defs.isNotEmpty()) append("<defs>\n").append(defs).append("</defs>\n")
        append(body)
        append("</svg>\n")
    }

    companion object {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        fun attrString(attrs: Array<out Pair<String, Any?>>): String = buildString {
            for ((k, v) in attrs) {
                if (v == null) continue
                val s = when (v) {
                    is Double -> v.f()
                    is Float -> v.toDouble().f()
                    else -> v.toString()
                }
                append(' ').append(k).append("=\"").append(esc(s)).append('"')
            }
        }
    }
}
