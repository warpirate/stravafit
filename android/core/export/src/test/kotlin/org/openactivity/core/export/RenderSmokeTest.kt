// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtTheme
import org.openactivity.core.export.file.ExportFormat
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders real bitmaps with Robolectric's native graphics to check the AndroidSVG pipeline end to end. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderSmokeTest {
    private val exporter = testExporter(ApplicationProvider.getApplicationContext())
    private val run = TestActivities.lakeRun()

    private fun Bitmap.opaquePixels(): Int {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.count { Color.alpha(it) > 0 }
    }

    @Test
    fun `aurora preview fills the frame with its themed background`() = runTest {
        val art = exporter.render(run, ArtSpec(template = ArtTemplate.AURORA, theme = ArtTheme.DUSK), scale = 0.25f)
        art as RenderedArt.Image
        assertEquals(270, art.bitmap.width)
        assertEquals(270, art.bitmap.height)
        assertEquals(art.bitmap.width * art.bitmap.height, art.bitmap.opaquePixels())
    }

    @Test
    fun `sticker is transparent except for the route`() = runTest {
        val art = exporter.render(run, ArtSpec(template = ArtTemplate.MINIMAL_STICKER), scale = 0.25f) as RenderedArt.Image
        val drawn = art.bitmap.opaquePixels()
        assertEquals(0, Color.alpha(art.bitmap.getPixel(0, 0)))
        assertTrue("route drawn: $drawn px", drawn > 200)
        assertTrue("mostly transparent: $drawn px", drawn < art.bitmap.width * art.bitmap.height / 2)
    }

    private fun Bitmap.opaqueBounds(): FloatArray {
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(getPixel(x, y)) > 0) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        return floatArrayOf(minX / width.toFloat(), minY / height.toFloat(), maxX / width.toFloat(), maxY / height.toFloat())
    }

    @Test
    fun `scaled preview shows the whole artwork, not its top-left corner`() = runTest {
        // Regression: previews at half size were cropped because the SVG had no viewBox.
        val spec = ArtSpec(template = ArtTemplate.MINIMAL_STICKER)
        val full = (exporter.render(run, spec, scale = 1f) as RenderedArt.Image).bitmap.opaqueBounds()
        val preview = (exporter.render(run, spec, scale = 0.25f) as RenderedArt.Image).bitmap.opaqueBounds()
        for (i in full.indices) assertEquals("bound $i", full[i], preview[i], 0.03f)
    }

    @Test
    fun `png export is a decodable png of the full output size`() = runTest {
        val file = exporter.exportImage(run, ArtSpec(template = ArtTemplate.STORY), ExportFormat.PNG)
        val bytes = file.file.readBytes()
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), bytes.copyOf(4))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1080, decoded.width)
        assertEquals(1920, decoded.height)
        assertEquals("image/png", file.mimeType)
    }

    @Test
    fun `every template renders`() = runTest {
        for (template in ArtTemplate.entries) {
            val art = exporter.render(run, ArtSpec(template = template), scale = 0.3f)
            assertTrue("$template: $art", art is RenderedArt.Image)
            assertTrue("$template draws pixels", (art as RenderedArt.Image).bitmap.opaquePixels() > 0)
        }
    }

    @Test
    fun `svg export writes the vector document`() = runTest {
        val file = exporter.exportImage(run, ArtSpec(template = ArtTemplate.POSTER), ExportFormat.SVG)
        val text = file.file.readText()
        assertTrue(text.startsWith("<svg"))
        assertTrue(file.displayName.endsWith(".svg"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PosterPdfTest {
    @Test
    fun `poster pdf is written`() = runTest {
        val exporter = testExporter(ApplicationProvider.getApplicationContext())
        val file = try {
            exporter.exportImage(TestActivities.lakeRun(), ArtSpec(template = ArtTemplate.POSTER), ExportFormat.PDF)
        } catch (e: IllegalStateException) {
            // Robolectric has no native PdfDocument backend; the page drawing above it still ran.
            assumeTrue("PdfDocument unsupported on this host: ${e.message}", false)
            return@runTest
        }
        val head = file.file.readBytes().copyOf(5).toString(Charsets.US_ASCII)
        assertEquals("%PDF-", head)
        assertEquals("application/pdf", file.mimeType)
    }
}
