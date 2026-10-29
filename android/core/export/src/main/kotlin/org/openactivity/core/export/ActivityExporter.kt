// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtResult
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.art.RouteArtComposer
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.file.TrackFileEncoder
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.privacyTrimmed
import org.openactivity.core.export.render.PaperSize
import org.openactivity.core.export.render.PosterPdfRenderer
import org.openactivity.core.export.render.SvgRasterizer
import javax.inject.Inject
import kotlin.math.roundToInt

/** Result of rendering an image: a bitmap, or the reason the template cannot show this activity. */
public sealed interface RenderedArt {
    /** The rendered [bitmap]; the caller owns it. */
    public data class Image(val bitmap: Bitmap) : RenderedArt

    /** Nothing can be drawn. */
    public data class Unavailable(val reason: ArtUnavailableReason) : RenderedArt
}

/** Thrown when an image is requested for an activity the template cannot show. */
public class ArtUnavailableException(public val reason: ArtUnavailableReason) : IllegalStateException("Image unavailable: $reason")

/** The two images Instagram stories use: a transparent sticker and a full 9:16 story picture. */
public data class StoryAssets(val sticker: ExportedFile, val storyImage: ExportedFile, val topColor: Int, val bottomColor: Int)

/**
 * Single entry point for the share screen: renders previews and writes every export format into the
 * export cache, ready for [org.openactivity.core.export.share.ShareLauncher] or the gallery.
 *
 * All work runs on [dispatcher] (default: [Dispatchers.Default]); every function is main-safe.
 */
public class ActivityExporter internal constructor(
    private val composer: RouteArtComposer,
    private val rasterizer: SvgRasterizer,
    private val pdfRenderer: PosterPdfRenderer,
    private val encoder: TrackFileEncoder,
    private val store: ExportFileStore,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    public constructor(
        composer: RouteArtComposer,
        rasterizer: SvgRasterizer,
        pdfRenderer: PosterPdfRenderer,
        encoder: TrackFileEncoder,
        store: ExportFileStore,
    ) : this(composer, rasterizer, pdfRenderer, encoder, store, Dispatchers.Default)

    /** Renders [spec] for [activity] at [scale] of the output size (previews use about half). */
    public suspend fun render(activity: ExportActivity, spec: ArtSpec, scale: Float = 1f): RenderedArt =
        withContext(dispatcher) {
            when (val art = composer.compose(activity, spec)) {
                is ArtResult.Unavailable -> RenderedArt.Unavailable(art.reason)
                is ArtResult.Svg -> RenderedArt.Image(
                    rasterizer.render(
                        art.svg,
                        (art.width * scale).roundToInt().coerceAtLeast(1),
                        (art.height * scale).roundToInt().coerceAtLeast(1),
                    ),
                )
            }
        }

    /** Full-size PNG bytes of [spec] for [activity], e.g. for saving to the gallery. */
    public suspend fun png(activity: ExportActivity, spec: ArtSpec): ByteArray = withContext(dispatcher) {
        val art = svgOrThrow(activity, spec)
        rasterizer.renderPng(art.svg, art.width, art.height)
    }

    /**
     * Writes [spec] as an image file: [ExportFormat.PNG], [ExportFormat.SVG] or [ExportFormat.PDF]
     * (a poster page of [paper] size).
     *
     * @throws ArtUnavailableException when the template cannot show this activity.
     */
    public suspend fun exportImage(
        activity: ExportActivity,
        spec: ArtSpec,
        format: ExportFormat,
        paper: PaperSize = PaperSize.A4,
    ): ExportedFile = withContext(dispatcher) {
        require(!format.isData) { "$format is a data format; use exportData" }
        val art = svgOrThrow(activity, spec)
        val name = fileName(activity, spec.template.name.lowercase())
        when (format) {
            ExportFormat.SVG -> store.write(name, format) { it.write(art.svg.toByteArray(Charsets.UTF_8)) }
            ExportFormat.PNG -> {
                val png = rasterizer.renderPng(art.svg, art.width, art.height)
                store.write(name, format) { it.write(png) }
            }
            ExportFormat.PDF -> store.write(name, format) {
                pdfRenderer.write(art.svg, art.width, art.height, composer.backgroundColor(spec.theme), it, paper)
            }
            else -> error("unreachable")
        }
    }

    /**
     * Writes [activity] as a track-data [format]. With [privacyTrim] the owner's privacy settings are
     * applied first (positions near the start and end are removed; sensor data is kept).
     *
     * @throws IllegalArgumentException when the activity lacks what the format needs.
     */
    public suspend fun exportData(activity: ExportActivity, format: ExportFormat, privacyTrim: Boolean): ExportedFile =
        withContext(dispatcher) {
            val source = if (privacyTrim) activity.privacyTrimmed().activity else activity
            val bytes = encoder.encode(source, format)
            store.write(fileName(activity, null), format) { it.write(bytes) }
        }

    /** Whether [format] can be written for [activity] (with [privacyTrim] applied as the export would). */
    public fun canExportData(activity: ExportActivity, format: ExportFormat, privacyTrim: Boolean): Boolean {
        val source = if (privacyTrim) activity.privacyTrimmed().activity else activity
        return encoder.unavailableReason(source, format) == null
    }

    /**
     * Prepares an Instagram story: a transparent sticker of the route plus a full story picture in the
     * chosen theme, and the theme colours for the story background gradient.
     *
     * @throws ArtUnavailableException when the activity has no route that may be shown.
     */
    public suspend fun storyAssets(activity: ExportActivity, spec: ArtSpec): StoryAssets = withContext(dispatcher) {
        val sticker = svgOrThrow(
            activity,
            spec.withTemplate(ArtTemplate.MINIMAL_STICKER).copy(aspect = ArtAspect.SQUARE, transparent = true),
        )
        val story = svgOrThrow(activity, spec.withTemplate(ArtTemplate.STORY).copy(transparent = false))
        val stickerPng = rasterizer.renderPng(sticker.svg, sticker.width, sticker.height)
        val storyPng = rasterizer.renderPng(story.svg, story.width, story.height)
        StoryAssets(
            sticker = store.write(fileName(activity, "sticker"), ExportFormat.PNG) { it.write(stickerPng) },
            storyImage = store.write(fileName(activity, "story"), ExportFormat.PNG) { it.write(storyPng) },
            topColor = composer.backgroundColor(spec.theme),
            bottomColor = composer.accentColor(spec.theme),
        )
    }

    private fun svgOrThrow(activity: ExportActivity, spec: ArtSpec): ArtResult.Svg =
        when (val art = composer.compose(activity, spec)) {
            is ArtResult.Svg -> art
            is ArtResult.Unavailable -> throw ArtUnavailableException(art.reason)
        }

    private fun fileName(activity: ExportActivity, suffix: String?): String {
        val date = activity.startTime?.atZone(activity.zone)?.toLocalDate()?.toString()
        return listOfNotNull(activity.title.ifBlank { null }, date, suffix).joinToString("_")
    }

    public companion object {
        /** Builds an exporter without dependency injection (tests, previews, non-Hilt callers). */
        public fun create(context: Context): ActivityExporter = create(ExportFileStore(context.applicationContext))

        internal fun create(store: ExportFileStore): ActivityExporter {
            val rasterizer = SvgRasterizer()
            return ActivityExporter(RouteArtComposer(), rasterizer, PosterPdfRenderer(rasterizer), TrackFileEncoder(), store)
        }
    }
}
