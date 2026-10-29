// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportFormat
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportFileProviderTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `provider is declared with the export authority`() {
        val info = context.packageManager.resolveContentProvider("${context.packageName}.export.fileprovider", 0)
        assertEquals("org.openactivity.core.export.share.ExportFileProvider", info!!.name)
    }

    @Test
    fun `real provider serves the export cache directory`() {
        assumeFalse("FileProvider path matching needs '/' separators", isWindowsHost)
        val file = ExportFileStore(context).write("run", ExportFormat.GPX) { it.write(1) }
        assertEquals("content://${context.packageName}.export.fileprovider/exports/run.gpx", file.uri.toString())
    }
}
