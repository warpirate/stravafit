// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider for export files. A dedicated subclass keeps its manifest entry from clashing with other
 * FileProviders merged into the app; it only serves `cache/exports/` (see `res/xml/export_file_paths.xml`).
 */
public class ExportFileProvider : FileProvider() {
    public companion object {
        /** Authority of this provider for the app identified by [context]'s package name. */
        public fun authority(context: Context): String = "${context.packageName}.export.fileprovider"

        /** A shareable `content://` URI for [file], which must live in the export cache directory. */
        public fun uriFor(context: Context, file: File): Uri = getUriForFile(context, authority(context), file)
    }
}
