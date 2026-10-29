// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import android.graphics.Bitmap
import androidx.annotation.StringRes
import org.openactivity.core.export.StoryAssets
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.file.FormatUnavailableReason

/** State of the share screen. */
public sealed interface ShareExportUiState {
    /** The activity is being loaded. */
    public data object Loading : ShareExportUiState

    /** No activity has the requested id. */
    public data object NotFound : ShareExportUiState

    /** Loading failed; [message] says why. */
    public data class LoadFailed(val message: String) : ShareExportUiState

    /**
     * The activity is loaded.
     *
     * @property title activity name, used in the preview's description.
     * @property spec current image settings.
     * @property preview the live preview of [spec].
     * @property colorByOptions colour modes with whether this activity has the data for each.
     * @property formats every export format with the reason it is unavailable (`null` when available).
     * @property instagramAvailable whether the Instagram story button is shown.
     * @property busy the action in progress, which disables the action buttons.
     * @property formatSheetOpen whether the export-file sheet is shown.
     */
    public data class Content(
        val title: String,
        val spec: ArtSpec,
        val preview: PreviewState,
        val colorByOptions: List<Pair<ArtColorBy, Boolean>>,
        val formats: List<Pair<ExportFormat, FormatUnavailableReason?>>,
        val instagramAvailable: Boolean,
        val busy: ShareAction? = null,
        val formatSheetOpen: Boolean = false,
        /** Metres hidden at the start and the finish while "Hide start and finish" is on. */
        val hiddenStartMeters: Int = 0,
        val hiddenEndMeters: Int = 0,
    ) : ShareExportUiState {
        /** True when the current template can be drawn, so image actions are enabled. */
        val imageAvailable: Boolean get() = preview !is PreviewState.Unavailable
    }
}

/** The live preview. */
public sealed interface PreviewState {
    /** A render is running; [previous] is shown dimmed meanwhile. */
    public data class Rendering(val previous: Bitmap?) : PreviewState

    /** The rendered preview. */
    public data class Ready(val bitmap: Bitmap) : PreviewState

    /** The template cannot draw this activity. */
    public data class Unavailable(val reason: ArtUnavailableReason) : PreviewState

    /** Rendering failed unexpectedly. */
    public data object Failed : PreviewState
}

/** Actions that produce a file or start another app. */
public enum class ShareAction { SHARE, STORY, SAVE_IMAGE, EXPORT_FILE }

/** Where an exported file goes. */
public enum class FileDestination { SHARE, SAVE_TO_DEVICE }

/** One-shot instructions for the screen, which owns the Activity context needed to carry them out. */
public sealed interface ShareEffect {
    /** Open the system share sheet for [file]. */
    public data class ShareFile(val file: ExportedFile) : ShareEffect

    /** Send [assets] to an Instagram story. */
    public data class ShareStory(val assets: StoryAssets) : ShareEffect

    /** Ask the user where to save [file] (Storage Access Framework). */
    public data class SaveDocument(val file: ExportedFile) : ShareEffect

    /** Request the legacy storage permission before saving to the gallery (Android 8–9). */
    public data object RequestStoragePermission : ShareEffect

    /** Show a short message. */
    public data class Message(@StringRes val text: Int, val args: List<String> = emptyList()) : ShareEffect
}
