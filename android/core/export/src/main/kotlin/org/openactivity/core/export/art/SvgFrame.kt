// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.art

/** Places one SVG document inside a larger frame, used to give fixed-shape templates another aspect ratio. */
internal object SvgFrame {
    private val ROOT_OPEN = Regex("""<svg\b[^>]*>""")

    /**
     * Returns an [outerWidth] x [outerHeight] SVG with [svg] ([innerWidth] x [innerHeight]) centred in it,
     * optionally over a solid [background] colour (`#RRGGBB`).
     */
    fun center(
        svg: String,
        innerWidth: Int,
        innerHeight: Int,
        outerWidth: Int,
        outerHeight: Int,
        background: String? = null,
    ): String {
        if (innerWidth == outerWidth && innerHeight == outerHeight && background == null) return svg
        val open = ROOT_OPEN.find(svg) ?: error("Not an SVG document")
        val close = svg.lastIndexOf("</svg>")
        check(close > open.range.last) { "Unterminated SVG document" }
        val body = svg.substring(open.range.last + 1, close)
        val x = (outerWidth - innerWidth) / 2
        val y = (outerHeight - innerHeight) / 2
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$outerWidth\" height=\"$outerHeight\" ")
            append("viewBox=\"0 0 $outerWidth $outerHeight\">\n")
            if (background != null) append("<rect width=\"$outerWidth\" height=\"$outerHeight\" fill=\"$background\"/>\n")
            append("<svg x=\"$x\" y=\"$y\" width=\"$innerWidth\" height=\"$innerHeight\" viewBox=\"0 0 $innerWidth $innerHeight\">")
            append(body)
            append("</svg>\n</svg>\n")
        }
    }
}
