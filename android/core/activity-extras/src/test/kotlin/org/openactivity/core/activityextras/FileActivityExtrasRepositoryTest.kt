// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileActivityExtrasRepositoryTest {
    private lateinit var context: Context
    private lateinit var root: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.filesDir, "extras-test").apply { deleteRecursively() }
    }

    private fun repository() = FileActivityExtrasRepository(root, context.contentResolver, Dispatchers.Unconfined)

    private fun picture(name: String, width: Int, height: Int, orientation: Int? = null): Uri {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 120, 140)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "47/1,22/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    @Test
    fun `unknown activity has no extras`() = runTest {
        assertEquals(ActivityExtras.NONE, repository().observe(7).first())
    }

    @Test
    fun `mute flag persists across instances`() = runTest {
        repository().setMuted(3, true)
        assertTrue(repository().get(3).muted)
        repository().setMuted(3, false)
        assertFalse(repository().get(3).muted)
    }

    @Test
    fun `photos are re-encoded upright, downscaled and stripped of location`() = runTest {
        val repo = repository()
        val result = repo.addPhotos(1, listOf(picture("wide.jpg", 4200, 1000, ExifInterface.ORIENTATION_ROTATE_90)))
        assertEquals(AddPhotosResult(added = 1, skippedOverLimit = 0, failed = 0), result)

        val photo = repo.observe(1).first().photos.single()
        // Rotated by 90 degrees, then the long side capped at 2048.
        assertEquals(PhotoEncoder.DEFAULT_MAX_SIDE, photo.height)
        assertTrue(photo.width < photo.height)
        val decoded = BitmapFactory.decodeFile(photo.file.path)
        assertEquals(photo.width, decoded.width)
        assertNull(ExifInterface(photo.file.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test
    fun `unreadable files fail alone and the limit is enforced`() = runTest {
        val repo = repository()
        val broken = File(context.cacheDir, "broken.jpg").apply { writeText("not a picture") }
        val uris = List(ActivityExtras.MAX_PHOTOS + 2) { picture("p$it.jpg", 64, 48) } + Uri.fromFile(broken)
        val result = repo.addPhotos(9, uris)
        assertEquals(ActivityExtras.MAX_PHOTOS, result.added)
        assertEquals(3, result.skippedOverLimit)
        assertEquals(ActivityExtras.MAX_PHOTOS, repository().get(9).photos.size)
    }

    @Test
    fun `removing a photo deletes its file and deleteAll clears everything`() = runTest {
        val repo = repository()
        repo.addPhotos(5, listOf(picture("a.jpg", 80, 60), picture("b.jpg", 80, 60)))
        repo.setMuted(5, true)
        val first = repo.get(5).photos.first()
        repo.removePhoto(5, first.id)
        assertFalse(first.file.exists())
        assertEquals(1, repo.get(5).photos.size)

        repo.deleteAll(5)
        assertEquals(ActivityExtras.NONE, repo.get(5))
        assertFalse(File(root, "5").exists())
    }

    @Test
    fun `a corrupt index reads as empty instead of crashing`() = runTest {
        File(root, "4").apply { mkdirs() }.resolve(FileActivityExtrasRepository.INDEX).writeText("{broken")
        assertEquals(ActivityExtras.NONE, repository().get(4))
    }

    @Test
    fun `sample size keeps the long side above the target`() {
        assertEquals(1, PhotoEncoder.sampleSize(2000, 1000, 2048))
        assertEquals(2, PhotoEncoder.sampleSize(4096, 3000, 2048))
        assertEquals(4, PhotoEncoder.sampleSize(9000, 3000, 2048))
    }
}
