// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Storage Access Framework picker for activity files: `ACTION_OPEN_DOCUMENT` with multiple selection.
 *
 * Document providers rarely know the GPX, TCX or FIT MIME types and report them as
 * `application/octet-stream` or `text/xml`, so the picker accepts those generic types too and the
 * [ActivityFileParser] decides by content. It never offers a wildcard: images, PDFs and the SVG/PNG
 * exports the app writes itself are not selectable. Launch it with [MIME_TYPES] (or [ROUTE_MIME_TYPES]):
 *
 * ```kotlin
 * val picker = rememberLauncherForActivityResult(ImportDocumentsContract()) { uris -> viewModel.import(uris) }
 * picker.launch(ImportDocumentsContract.MIME_TYPES)
 * ```
 */
public class ImportDocumentsContract : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)

    public companion object {
        /** MIME types offered when importing activities (GPX, TCX, FIT, optionally gzipped). */
        public val MIME_TYPES: Array<String> = arrayOf(
            "application/gpx+xml",
            "application/vnd.garmin.tcx+xml",
            "application/vnd.ant.fit",
            "application/octet-stream",
            "application/xml",
            "text/xml",
            "application/gzip",
        )

        /** MIME types offered when importing a GPX route. */
        public val ROUTE_MIME_TYPES: Array<String> = arrayOf(
            "application/gpx+xml",
            "application/octet-stream",
            "application/xml",
            "text/xml",
        )
    }
}
