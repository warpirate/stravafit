// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.render.SvgRasterizer
import org.openactivity.core.export.render.toPng
import routeexport.CardStyle
import routeexport.PeriodCard
import routeexport.RouteArt
import routeexport.periodCard
import javax.inject.Inject

/**
 * Draws period cards through the share screen's pipeline: route-art SVG, rasterised by [SvgRasterizer]
 * (so the PNG has exactly the vector geometry), written to the export cache for the share sheet.
 */
class PeriodCardRenderer internal constructor(
    private val rasterizer: SvgRasterizer,
    private val store: ExportFileStore?,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(store: ExportFileStore) : this(SvgRasterizer(), store, Dispatchers.Default)

    /** A preview of [card] at [scale] of the full 1080×1350 size; the sticker keeps its transparency. */
    suspend fun preview(card: PeriodCard, style: CardStyle, scale: Float = PREVIEW_SCALE): Bitmap = withContext(dispatcher) {
        rasterizer.render(RouteArt.periodCard(card, style), (WIDTH * scale).toInt(), (HEIGHT * scale).toInt())
    }

    /** The full-size PNG of [card], ready to share. [name] becomes the file name. */
    suspend fun export(card: PeriodCard, style: CardStyle, name: String): ExportedFile = withContext(dispatcher) {
        val target = checkNotNull(store) { "no export store" }
        val bitmap = rasterizer.render(RouteArt.periodCard(card, style), WIDTH, HEIGHT)
        try {
            val png = bitmap.toPng()
            target.write(if (style == CardStyle.STICKER) "$name sticker" else name, "png", "image/png") { it.write(png) }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val WIDTH: Int = 1080
        const val HEIGHT: Int = 1350
        const val PREVIEW_SCALE: Float = 0.5f

        /** For previews and tests: renders on the caller's dispatcher and cannot export. */
        fun forPreview(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): PeriodCardRenderer =
            PeriodCardRenderer(SvgRasterizer(), null, dispatcher)
    }
}
