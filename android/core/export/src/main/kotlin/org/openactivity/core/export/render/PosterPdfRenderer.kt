// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.render

import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.caverock.androidsvg.RenderOptions
import java.io.OutputStream
import javax.inject.Inject
import kotlin.math.min

/** ISO paper sizes in PostScript points (1/72 inch). */
public enum class PaperSize(public val widthPt: Int, public val heightPt: Int, public val label: String) {
    A4(595, 842, "A4"),
    A3(842, 1191, "A3"),
    A2(1191, 1684, "A2"),
}

/**
 * Writes a route-art poster as a one-page PDF with [PdfDocument]. The SVG is drawn onto the page canvas,
 * so paths and text stay vectors and print sharply at any size.
 */
public class PosterPdfRenderer @Inject constructor(private val rasterizer: SvgRasterizer) {

    /**
     * Draws [svg] ([svgWidth] x [svgHeight] user units) centred on a [paper] page filled with
     * [backgroundColor] (ARGB), and writes the PDF to [output]. The stream is not closed.
     */
    public fun write(
        svg: String,
        svgWidth: Int,
        svgHeight: Int,
        backgroundColor: Int,
        output: OutputStream,
        paper: PaperSize = PaperSize.A4,
    ) {
        val document = rasterizer.parse(svg)
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(paper.widthPt, paper.heightPt, 1).create())
            val canvas = page.canvas
            canvas.drawColor(backgroundColor)
            val scale = min(paper.widthPt.toFloat() / svgWidth, paper.heightPt.toFloat() / svgHeight)
            val w = svgWidth * scale
            val h = svgHeight * scale
            val left = (paper.widthPt - w) / 2
            val top = (paper.heightPt - h) / 2
            val viewPort = RectF(left, top, left + w, top + h)
            document.renderToCanvas(canvas, RenderOptions.create().viewPort(viewPort.left, viewPort.top, viewPort.width(), viewPort.height()))
            pdf.finishPage(page)
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }
}
