// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import org.openactivity.core.export.file.ExportedFile

/**
 * `ACTION_CREATE_DOCUMENT` for an export file: the save dialog is pre-filled with the file's name and
 * filtered to its MIME type. Returns the created document's URI, or `null` when cancelled.
 */
internal class CreateDocumentForFile : ActivityResultContract<ExportedFile, Uri?>() {
    override fun createIntent(context: Context, input: ExportedFile): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.displayName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent.takeIf { resultCode == android.app.Activity.RESULT_OK }?.data
}
