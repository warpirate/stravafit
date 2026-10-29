// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import android.content.Context
import android.net.Uri
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.share.ExportFileProvider

/**
 * androidx FileProvider matches roots with '/' separators, so it cannot serve files on a Windows host
 * JVM. Robolectric tests therefore build the same `content://` URI directly; the real provider is
 * exercised on non-Windows hosts by [ExportFileProviderTest].
 */
val isWindowsHost: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

fun testFileStore(context: Context): ExportFileStore = ExportFileStore(context) { ctx, file ->
    if (isWindowsHost) {
        Uri.parse("content://${ExportFileProvider.authority(ctx)}/${ExportFileStore.DIRECTORY}/${Uri.encode(file.name)}")
    } else {
        ExportFileProvider.uriFor(ctx, file)
    }
}

fun testExporter(context: Context): ActivityExporter = ActivityExporter.create(testFileStore(context))
