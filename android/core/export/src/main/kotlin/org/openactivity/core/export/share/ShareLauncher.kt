// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import org.openactivity.core.export.file.ExportedFile
import javax.inject.Inject

/**
 * Starts share intents built by [ShareIntents].
 *
 * The Instagram sticker intent needs a Facebook App ID, which every distributor registers for itself.
 * Builds that have one declare it in the app manifest:
 *
 * ```xml
 * <meta-data android:name="org.openactivity.export.STORY_SOURCE_APPLICATION" android:value="1234567890" />
 * ```
 *
 * Without it, the story image is sent to Instagram with a normal share intent instead.
 */
public class ShareLauncher @Inject constructor(@ApplicationContext private val appContext: Context) {

    /** True when Instagram is installed and can receive images. */
    public fun isInstagramAvailable(): Boolean =
        resolves(ShareIntents.sendIntent(android.net.Uri.EMPTY, "image/png").setPackage(ShareIntents.INSTAGRAM_PACKAGE))

    /** The Facebook App ID declared in the manifest, or `null` when the build has none. */
    public fun storySourceApplication(): String? = try {
        val info = appContext.packageManager.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
        info.metaData?.get(META_STORY_SOURCE_APPLICATION)?.toString()?.trim()?.ifEmpty { null }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Opens the system share sheet for [file]. Returns false when no app can receive it. */
    public fun share(context: Context, file: ExportedFile, chooserTitle: CharSequence? = null): Boolean =
        start(context, ShareIntents.send(file.uri, file.mimeType, chooserTitle, subject = file.displayName))

    /**
     * Shares to an Instagram story. With a configured App ID, [sticker] goes in as a movable sticker over
     * a [topColor]→[bottomColor] gradient; otherwise the finished [storyImage] is sent to Instagram.
     * Returns false when Instagram cannot take it, so the caller can fall back to [share].
     */
    public fun shareToInstagramStory(
        context: Context,
        sticker: ExportedFile,
        storyImage: ExportedFile,
        topColor: Int,
        bottomColor: Int,
    ): Boolean {
        val appId = storySourceApplication()
        if (appId != null) {
            val intent = ShareIntents.instagramStorySticker(
                sticker.uri, ShareIntents.hexColor(topColor), ShareIntents.hexColor(bottomColor), appId,
            )
            if (resolves(intent)) {
                context.grantUriPermission(ShareIntents.INSTAGRAM_PACKAGE, sticker.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (start(context, intent)) return true
            }
        }
        val fallback = ShareIntents.instagramImage(storyImage.uri)
        if (!resolves(fallback)) return false
        context.grantUriPermission(ShareIntents.INSTAGRAM_PACKAGE, storyImage.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, fallback)
    }

    private fun resolves(intent: Intent): Boolean {
        val pm = appContext.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        }
        return info != null
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    public companion object {
        /** Manifest meta-data key holding the Facebook App ID for story stickers. */
        public const val META_STORY_SOURCE_APPLICATION: String = "org.openactivity.export.STORY_SOURCE_APPLICATION"
    }
}
