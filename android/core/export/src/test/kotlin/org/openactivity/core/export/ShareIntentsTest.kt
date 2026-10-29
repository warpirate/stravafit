// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.share.ShareIntents
import org.openactivity.core.export.share.ShareLauncher
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val uri = Uri.parse("content://org.example.export.fileprovider/exports/run.png")

    @Test
    fun `generic share grants read access through clip data inside a chooser`() {
        val chooser = ShareIntents.send(uri, "image/png", "Share", subject = "run.png")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("run.png", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    @Test
    fun `instagram story intent carries sticker and background colours`() {
        val intent = ShareIntents.instagramStorySticker(uri, "#101114", "#7DD3FC", "123456")
        assertEquals("com.instagram.share.ADD_TO_STORY", intent.action)
        assertEquals("com.instagram.android", intent.`package`)
        assertEquals("image/png", intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<Uri>("interactive_asset_uri"))
        assertEquals("#101114", intent.getStringExtra("top_background_color"))
        assertEquals("#7DD3FC", intent.getStringExtra("bottom_background_color"))
        assertEquals("123456", intent.getStringExtra("source_application"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `hex colours drop alpha`() {
        assertEquals("#0B0F14", ShareIntents.hexColor(0xFF0B0F14.toInt()))
    }

    @Test
    fun `export files get a content uri from the export provider`() {
        val file = testFileStore(context).write("Morning Run", ExportFormat.GPX) { it.write("<gpx/>".toByteArray()) }
        assertEquals("content", file.uri.scheme)
        assertEquals("${context.packageName}.export.fileprovider", file.uri.authority)
        assertEquals("Morning_Run.gpx", file.displayName)
        assertTrue(file.file.exists())
        assertFalse(java.io.File(file.file.parentFile, "Morning_Run.gpx.part").exists())
    }

    @Test
    fun `file name stems are portable`() {
        assertEquals("Cafe_loop_5k", ExportFileStore.fileNameStem("Café loop / 5k"))
        assertEquals("activity", ExportFileStore.fileNameStem("???"))
        assertTrue(ExportFileStore.fileNameStem("x".repeat(200)).length <= 60)
    }

    @Test
    fun `share launcher starts the chooser and falls back when instagram is missing`() = runTest {
        val exporter = testExporter(context)
        val run = TestActivities.lakeRun()
        val file = exporter.exportData(run, ExportFormat.GPX, privacyTrim = true)
        val launcher = ShareLauncher(context)
        assertTrue(launcher.share(context, file))
        val started = shadowOf(context).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)

        assertFalse(launcher.isInstagramAvailable())
        val assets = exporter.storyAssets(run, org.openactivity.core.export.art.ArtSpec())
        assertFalse(launcher.shareToInstagramStory(context, assets.sticker, assets.storyImage, assets.topColor, assets.bottomColor))
    }
}
