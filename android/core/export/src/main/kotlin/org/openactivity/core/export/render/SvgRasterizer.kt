// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Thrown when an SVG document cannot be parsed or rendered. */
public class SvgRenderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Rasterises route-art SVG documents with AndroidSVG (Apache-2.0), so PNGs show exactly the geometry
 * of the vector export. Filters (glow) and blend modes are not supported by AndroidSVG and are simply
 * left out of the bitmap; everything else (paths, gradients, patterns, text) is drawn.
 */
public class SvgRasterizer @Inject constructor() {

    /**
     * Renders [svg] scaled to [width] x [height] pixels. When [backgroundColor] (ARGB) is `null` the
     * bitmap keeps a transparent background. Call off the main thread: large posters take a while.
     *
     * @throws SvgRenderException if the document is not valid SVG.
     */
    public fun render(svg: String, width: Int, height: Int, backgroundColor: Int? = null): Bitmap {
        require(width > 0 && height > 0) { "Bitmap size must be positive: ${width}x$height" }
        val document = parse(svg)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (backgroundColor != null) canvas.drawColor(backgroundColor)
        document.renderToCanvas(canvas, RenderOptions.create().viewPort(0f, 0f, width.toFloat(), height.toFloat()))
        return bitmap
    }

    /** Renders [svg] and returns it as PNG bytes. The intermediate bitmap is recycled. */
    public fun renderPng(svg: String, width: Int, height: Int, backgroundColor: Int? = null): ByteArray {
        val bitmap = render(svg, width, height, backgroundColor)
        try {
            return bitmap.toPng()
        } finally {
            bitmap.recycle()
        }
    }

    internal fun parse(svg: String): SVG = try {
        SVG.getFromString(svg)
    } catch (e: SVGParseException) {
        throw SvgRenderException("Invalid SVG: ${e.message}", e)
    }
}

/** Encodes this bitmap as a lossless PNG. */
public fun Bitmap.toPng(): ByteArray {
    val out = ByteArrayOutputStream(width * height / 4)
    check(compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
    return out.toByteArray()
}
