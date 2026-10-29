// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The finish marker of the strava direction: a round chequered flag (dark and white squares) inside a
 * white ring. Drawn in code so no image asset is needed; shared by the live map and the thumbnails.
 */
internal object MarkerBitmaps {
    /** Draws the chequered finish marker centred on ([cx], [cy]) with outer [radius] px. */
    fun drawCheckered(canvas: Canvas, cx: Float, cy: Float, radius: Float, ringWidth: Float, dark: Int, ring: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ring
        canvas.drawCircle(cx, cy, radius, paint)
        val inner = radius - ringWidth
        val clip = Path().apply { addCircle(cx, cy, inner, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, inner, paint)
        paint.color = dark
        val cell = inner * 2 / CELLS
        val left = cx - inner
        val top = cy - inner
        for (row in 0 until CELLS) {
            for (col in 0 until CELLS) {
                if ((row + col) % 2 == 0) {
                    canvas.drawRect(RectF(left + col * cell, top + row * cell, left + (col + 1) * cell, top + (row + 1) * cell), paint)
                }
            }
        }
        canvas.restore()
    }

    /** A square bitmap of the chequered marker, [radiusPx] outer radius, for a map symbol layer. */
    fun checkered(radiusPx: Float, ringPx: Float, dark: Int, ring: Int): Bitmap {
        val size = kotlin.math.ceil(radiusPx * 2).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawCheckered(Canvas(bitmap), size / 2f, size / 2f, radiusPx, ringPx, dark, ring)
        return bitmap
    }

    /** Squares across the flag; four reads as chequered even at marker size. */
    private const val CELLS = 4
}
