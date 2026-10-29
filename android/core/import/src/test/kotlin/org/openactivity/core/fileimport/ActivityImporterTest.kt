// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityImporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val root = File(requireNotNull(System.getProperty("testdata.dir")))

    private fun copy(path: String, as_: String = File(path).name): Uri {
        val target = File(context.cacheDir, as_)
        File(root, path).copyTo(target, overwrite = true)
        return Uri.fromFile(target)
    }

    @Test
    fun `imports several files and reports errors per file`() = runTest {
        val importer = ActivityImporter.create(context)
        val results = importer.importAll(
            listOf(
                copy("gpx/garmin-edge-tpx-v1.gpx"),
                copy("gpx/not-gpx.xml"),
                copy("tcx/forerunner-run-2laps.tcx"),
                Uri.fromFile(File(context.cacheDir, "missing.fit")),
            ),
        )
        assertEquals(4, results.size)
        assertTrue(results[0] is FileImportResult.Success)
        assertEquals("garmin-edge-tpx-v1.gpx", results[0].displayName)
        assertEquals(ImportErrorKind.UNSUPPORTED_FORMAT, (results[1] as FileImportResult.Failure).error.kind)
        assertTrue(results[2] is FileImportResult.Success)
        assertEquals(ImportErrorKind.READ_FAILED, (results[3] as FileImportResult.Failure).error.kind)
    }

    @Test
    fun `the same file picked twice is imported once`() = runTest {
        val results = ActivityImporter.create(context).importAll(
            listOf(copy("gpx/garmin-edge-tpx-v1.gpx"), copy("gpx/garmin-edge-tpx-v1.gpx", "copy.gpx")),
        )
        assertTrue(results[0] is FileImportResult.Success)
        val second = results[1] as FileImportResult.Failure
        assertEquals("copy.gpx", second.displayName)
        assertEquals(ImportErrorKind.EMPTY, second.error.kind)
    }

    @Test
    fun `picker opens openable documents with multiple selection`() {
        val intent = ImportDocumentsContract().createIntent(context, ImportDocumentsContract.MIME_TYPES)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertTrue("application/gpx+xml" in intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!)
        assertTrue("*/*" !in intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!)
    }
}
