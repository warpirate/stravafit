// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/**
 * Builders for the intents the share screen sends. They are pure functions of their inputs so they can
 * be unit-tested; [ShareLauncher] starts them.
 */
public object ShareIntents {
    /** Package of the Instagram app. */
    public const val INSTAGRAM_PACKAGE: String = "com.instagram.android"

    /** Action of Instagram's "add to story" entry point (Meta "Sharing to Stories"). */
    public const val ACTION_ADD_TO_STORY: String = "com.instagram.share.ADD_TO_STORY"

    /** Extra naming the sharing app; Meta requires a registered Facebook App ID here. */
    public const val EXTRA_SOURCE_APPLICATION: String = "source_application"

    /** Extra holding the sticker image URI. */
    public const val EXTRA_INTERACTIVE_ASSET_URI: String = "interactive_asset_uri"

    /** Extra for the top colour of the story's background gradient (`#RRGGBB`). */
    public const val EXTRA_TOP_BACKGROUND_COLOR: String = "top_background_color"

    /** Extra for the bottom colour of the story's background gradient (`#RRGGBB`). */
    public const val EXTRA_BOTTOM_BACKGROUND_COLOR: String = "bottom_background_color"

    /**
     * A plain `ACTION_SEND` of one file with read permission granted through its [ClipData], wrapped in
     * the system chooser titled [chooserTitle].
     */
    public fun send(
        uri: Uri,
        mimeType: String,
        chooserTitle: CharSequence? = null,
        subject: String? = null,
        text: String? = null,
    ): Intent {
        val send = sendIntent(uri, mimeType, subject, text)
        return Intent.createChooser(send, chooserTitle)
    }

    /** The bare `ACTION_SEND` intent used by [send], without the chooser. */
    public fun sendIntent(uri: Uri, mimeType: String, subject: String? = null, text: String? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Instagram's story composer with [stickerUri] (a transparent PNG) as a movable sticker over a
     * gradient from [topColor] to [bottomColor] (`#RRGGBB`). [sourceApplication] is the Facebook App ID
     * the build registered with Meta. The caller must also grant Instagram read access to the URI.
     */
    public fun instagramStorySticker(
        stickerUri: Uri,
        topColor: String,
        bottomColor: String,
        sourceApplication: String,
    ): Intent = Intent(ACTION_ADD_TO_STORY).apply {
        setPackage(INSTAGRAM_PACKAGE)
        type = "image/png"
        putExtra(EXTRA_SOURCE_APPLICATION, sourceApplication)
        putExtra(EXTRA_INTERACTIVE_ASSET_URI, stickerUri)
        putExtra(EXTRA_TOP_BACKGROUND_COLOR, topColor)
        putExtra(EXTRA_BOTTOM_BACKGROUND_COLOR, bottomColor)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Sends a finished story image straight to Instagram, which then offers Story, Feed or Chat. Used when
     * the build has no Facebook App ID for the sticker intent.
     */
    public fun instagramImage(imageUri: Uri): Intent =
        sendIntent(imageUri, "image/png").apply { setPackage(INSTAGRAM_PACKAGE) }

    /** Formats an ARGB colour as `#RRGGBB` for the story extras. */
    public fun hexColor(argb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)
}
