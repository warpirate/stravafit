// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.art

/** Output frame of an image, in pixels. */
public enum class ArtAspect(public val width: Int, public val height: Int, public val label: String) {
    SQUARE(1080, 1080, "1:1"),
    PORTRAIT(1080, 1350, "4:5"),
    STORY(1080, 1920, "9:16"),
    LANDSCAPE(1920, 1080, "16:9"),
    POSTER(1200, 1600, "3:4"),
    BANNER(1200, 360, "10:3"),
    ;

    /** Width divided by height. */
    public val ratio: Float get() = width.toFloat() / height
}

/**
 * The image templates offered on the share screen.
 *
 * @property aspects frames the template can be rendered in; the first one is the default.
 * @property needsRoute true for templates that draw the map; false for the elevation chart, which reveals
 *   no location and is therefore not affected by privacy trimming.
 * @property supportsColorBy whether the route colour can follow a metric.
 * @property transparentByDefault stickers keep a transparent background so they can sit on a photo.
 */
public enum class ArtTemplate(
    public val label: String,
    public val aspects: List<ArtAspect>,
    public val needsRoute: Boolean,
    public val supportsColorBy: Boolean,
    public val transparentByDefault: Boolean,
) {
    MINIMAL_STICKER(
        "Minimal sticker",
        listOf(ArtAspect.SQUARE, ArtAspect.PORTRAIT, ArtAspect.STORY, ArtAspect.LANDSCAPE),
        needsRoute = true, supportsColorBy = false, transparentByDefault = true,
    ),
    AURORA(
        "Aurora",
        listOf(ArtAspect.SQUARE, ArtAspect.PORTRAIT, ArtAspect.STORY, ArtAspect.LANDSCAPE),
        needsRoute = true, supportsColorBy = true, transparentByDefault = false,
    ),
    POSTER("Poster", listOf(ArtAspect.POSTER), needsRoute = true, supportsColorBy = true, transparentByDefault = false),
    STORY("Story", listOf(ArtAspect.STORY), needsRoute = true, supportsColorBy = true, transparentByDefault = false),
    ELEVATION("Elevation", listOf(ArtAspect.BANNER), needsRoute = false, supportsColorBy = false, transparentByDefault = false),
}

/** What drives the route colour. Metrics missing from an activity fall back to a progress gradient. */
public enum class ArtColorBy(public val label: String) {
    PACE("Pace"),
    HEART_RATE("Heart rate"),
    ELEVATION("Elevation"),
    SOLID("Solid"),
}

/** Colour themes, mapped onto the route-art library's palettes. */
public enum class ArtTheme(public val label: String) {
    MIDNIGHT("Midnight"),
    DUSK("Dusk"),
    FOREST("Forest"),
    PAPER("Paper"),
}

/** Distance units for the stats printed on images. */
public enum class ArtUnits { METRIC, IMPERIAL }

/**
 * Everything that determines one rendered image.
 *
 * @property transparent draw no background (stickers, stories over photos). Ignored by templates that
 *   always need a background (poster, elevation).
 * @property privacyTrim hide the start and end according to the owner's privacy settings.
 */
public data class ArtSpec(
    val template: ArtTemplate = ArtTemplate.AURORA,
    val aspect: ArtAspect = template.aspects.first(),
    val theme: ArtTheme = ArtTheme.MIDNIGHT,
    val colorBy: ArtColorBy = ArtColorBy.PACE,
    val transparent: Boolean = template.transparentByDefault,
    val privacyTrim: Boolean = true,
    val units: ArtUnits = ArtUnits.METRIC,
) {
    init {
        require(aspect in template.aspects) { "${template.name} cannot be rendered as ${aspect.name}" }
    }

    /** A copy with [newTemplate], keeping the aspect when supported and resetting it otherwise. */
    public fun withTemplate(newTemplate: ArtTemplate): ArtSpec = copy(
        template = newTemplate,
        aspect = if (aspect in newTemplate.aspects) aspect else newTemplate.aspects.first(),
        transparent = newTemplate.transparentByDefault,
    )
}
