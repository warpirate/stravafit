// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.export.ActivityExporter
import org.openactivity.core.export.ArtUnavailableException
import org.openactivity.core.export.RenderedArt
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtTheme
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.core.export.model.ExportActivity
import org.openactivity.core.export.model.privacyTrimmed
import org.openactivity.core.export.share.GallerySaver
import org.openactivity.core.export.share.ShareLauncher

/**
 * Drives the share screen: loads the activity through [provider], keeps the image settings, renders a
 * debounced live preview and turns button presses into files plus [ShareEffect]s for the screen.
 */
@OptIn(FlowPreview::class)
public class ShareExportViewModel(
    private val activityId: String,
    private val provider: ExportActivityProvider,
    private val exporter: ActivityExporter,
    private val gallery: GallerySaver,
    private val instagramAvailable: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareExportUiState>(ShareExportUiState.Loading)

    /** Current screen state. */
    public val uiState: StateFlow<ShareExportUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ShareEffect>(Channel.BUFFERED)

    /** One-shot effects; collect them once, from the screen. */
    public val effects: Flow<ShareEffect> = _effects.receiveAsFlow()

    private var activity: ExportActivity? = null
    private val spec = MutableStateFlow<ArtSpec?>(null)

    init {
        load()
        viewModelScope.launch {
            spec.filterNotNull().debounce(PREVIEW_DEBOUNCE_MILLIS).collectLatest(::renderPreview)
        }
    }

    /** Loads (or reloads after a failure) the activity. */
    public fun load() {
        _uiState.value = ShareExportUiState.Loading
        viewModelScope.launch {
            val loaded = try {
                provider.load(activityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ShareExportUiState.LoadFailed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            if (loaded == null) {
                _uiState.value = ShareExportUiState.NotFound
                return@launch
            }
            activity = loaded
            val units = runCatching { provider.units() }.getOrDefault(org.openactivity.core.export.art.ArtUnits.METRIC)
            val initial = ArtSpec(colorBy = defaultColorBy(loaded), units = units)
            _uiState.value = ShareExportUiState.Content(
                title = loaded.title,
                spec = initial,
                preview = PreviewState.Rendering(previous = null),
                colorByOptions = colorByOptions(loaded),
                formats = formats(loaded, initial),
                instagramAvailable = instagramAvailable,
            )
            spec.value = initial
        }
    }

    /** Picks a template, keeping the current aspect when the template supports it. */
    public fun selectTemplate(template: ArtTemplate): Unit = updateSpec { it.withTemplate(template) }

    /** Picks an output size. */
    public fun selectAspect(aspect: ArtAspect): Unit = updateSpec {
        if (aspect in it.template.aspects) it.copy(aspect = aspect) else it
    }

    /** Picks a colour theme. */
    public fun selectTheme(theme: ArtTheme): Unit = updateSpec { it.copy(theme = theme) }

    /** Picks what colours the route. */
    public fun selectColorBy(colorBy: ArtColorBy): Unit = updateSpec { it.copy(colorBy = colorBy) }

    /** Turns privacy trimming of images and files on or off. */
    public fun setPrivacyTrim(enabled: Boolean): Unit = updateSpec { it.copy(privacyTrim = enabled) }

    /** Turns the transparent background on or off. */
    public fun setTransparent(enabled: Boolean): Unit = updateSpec { it.copy(transparent = enabled) }

    /** Opens or closes the export-file sheet. */
    public fun setFormatSheetOpen(open: Boolean): Unit = updateContent { it.copy(formatSheetOpen = open) }

    /** Shares the current image through the system share sheet. */
    public fun share(): Unit = runAction(ShareAction.SHARE) { a, s ->
        _effects.send(ShareEffect.ShareFile(exporter.exportImage(a, s, ExportFormat.PNG)))
    }

    /** Prepares an Instagram story from the current settings. */
    public fun shareToStory(): Unit = runAction(ShareAction.STORY) { a, s ->
        _effects.send(ShareEffect.ShareStory(exporter.storyAssets(a, s)))
    }

    /** Saves the current image to the gallery, asking for the legacy permission first where needed. */
    public fun saveImage() {
        if (gallery.needsPermission()) {
            _effects.trySend(ShareEffect.RequestStoragePermission)
            return
        }
        runAction(ShareAction.SAVE_IMAGE) { a, s ->
            val png = exporter.png(a, s)
            val name = ExportFileStore.fileNameStem("${a.title}_${s.template.name.lowercase()}") + ".png"
            gallery.saveImage(png, name)
            _effects.send(ShareEffect.Message(R.string.share_export_saved, listOf(GallerySaver.ALBUM)))
        }
    }

    /** Result of the storage permission request triggered by [saveImage]. */
    public fun onStoragePermissionResult(granted: Boolean) {
        if (granted) saveImage() else _effects.trySend(ShareEffect.Message(R.string.share_export_permission_denied))
    }

    /** Writes [format] and shares it or asks where to save it, per [destination]. */
    public fun exportFile(format: ExportFormat, destination: FileDestination): Unit =
        runAction(ShareAction.EXPORT_FILE) { a, s ->
            val file = if (format.isData) {
                exporter.exportData(a, format, privacyTrim = s.privacyTrim)
            } else {
                exporter.exportImage(a, s, format)
            }
            updateContent { it.copy(formatSheetOpen = false) }
            _effects.send(
                when (destination) {
                    FileDestination.SHARE -> ShareEffect.ShareFile(file)
                    FileDestination.SAVE_TO_DEVICE -> ShareEffect.SaveDocument(file)
                },
            )
        }

    /** Copies [file] to the document the user created for it. */
    public fun writeDocument(file: ExportedFile, destination: Uri) {
        viewModelScope.launch {
            try {
                gallery.copyToDocument(file.file, destination)
                _effects.send(ShareEffect.Message(R.string.share_export_saved_document, listOf(file.displayName)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.send(ShareEffect.Message(R.string.share_export_failed, listOf(e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    /** Reports that no app could receive a share, or that Instagram refused the story. */
    public fun onShareFailed(story: Boolean) {
        _effects.trySend(ShareEffect.Message(if (story) R.string.share_export_story_unavailable else R.string.share_export_no_app))
    }

    private fun updateSpec(change: (ArtSpec) -> ArtSpec) {
        val current = spec.value ?: return
        val next = change(current)
        if (next == current) return
        val loaded = activity ?: return
        updateContent { content ->
            // Keep showing the last finished image while renders are superseded in quick succession.
            val previous = when (val preview = content.preview) {
                is PreviewState.Ready -> preview.bitmap
                is PreviewState.Rendering -> preview.previous
                else -> null
            }
            content.copy(spec = next, preview = PreviewState.Rendering(previous), formats = formats(loaded, next))
        }
        spec.value = next
    }

    private fun updateContent(change: (ShareExportUiState.Content) -> ShareExportUiState.Content) {
        _uiState.update { state -> if (state is ShareExportUiState.Content) change(state) else state }
    }

    private suspend fun renderPreview(current: ArtSpec) {
        val loaded = activity ?: return
        val preview = try {
            when (val art = exporter.render(loaded, current, PREVIEW_SCALE)) {
                is RenderedArt.Image -> PreviewState.Ready(art.bitmap)
                is RenderedArt.Unavailable -> PreviewState.Unavailable(art.reason)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PreviewState.Failed
        }
        updateContent { if (it.spec == current) it.copy(preview = preview) else it }
    }

    private fun runAction(action: ShareAction, block: suspend (ExportActivity, ArtSpec) -> Unit) {
        val loaded = activity ?: return
        val current = spec.value ?: return
        val content = _uiState.value as? ShareExportUiState.Content ?: return
        if (content.busy != null) return
        updateContent { it.copy(busy = action) }
        viewModelScope.launch {
            try {
                block(loaded, current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArtUnavailableException) {
                _effects.send(ShareEffect.Message(R.string.share_export_failed, listOf(e.reason.name.lowercase().replace('_', ' '))))
            } catch (e: Exception) {
                _effects.send(ShareEffect.Message(R.string.share_export_failed, listOf(e.message ?: e.javaClass.simpleName)))
            } finally {
                updateContent { it.copy(busy = null) }
            }
        }
    }

    private fun formats(loaded: ExportActivity, current: ArtSpec): List<Pair<ExportFormat, FormatUnavailableReason?>> {
        val imageReason = exporter.imageUnavailableReason(loaded, current)
        // This runs on the main thread for every settings change: trim once (and only when the toggle
        // changes) instead of once per data format.
        val dataSource = if (current.privacyTrim) trimmedActivity(loaded) else loaded
        return ExportFormat.entries.map { format ->
            format to if (format.isData) exporter.dataUnavailableReason(dataSource, format, privacyTrim = false) else imageReason
        }
    }

    private var trimmed: Pair<ExportActivity, ExportActivity>? = null

    private fun trimmedActivity(loaded: ExportActivity): ExportActivity =
        trimmed?.takeIf { it.first === loaded }?.second
            ?: loaded.privacyTrimmed().activity.also { trimmed = loaded to it }

    public companion object {
        /** Previews render at half the output resolution. */
        public const val PREVIEW_SCALE: Float = 0.5f

        /** Settings changes within this window produce a single render. */
        public const val PREVIEW_DEBOUNCE_MILLIS: Long = 150L

        internal fun defaultColorBy(activity: ExportActivity): ArtColorBy =
            if (activity.hasTimes && activity.hasRoute) ArtColorBy.PACE else ArtColorBy.SOLID

        internal fun colorByOptions(activity: ExportActivity): List<Pair<ArtColorBy, Boolean>> = listOf(
            ArtColorBy.PACE to (activity.hasTimes && activity.hasRoute),
            ArtColorBy.HEART_RATE to activity.hasHeartRate,
            ArtColorBy.ELEVATION to activity.hasElevation,
            ArtColorBy.SOLID to true,
        )

        /** Factory used by the navigation entry point; builds the export services from [context]. */
        public fun factory(activityId: String, provider: ExportActivityProvider, context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return viewModelFactory {
                initializer {
                    ShareExportViewModel(
                        activityId = activityId,
                        provider = provider,
                        exporter = ActivityExporter.create(app),
                        gallery = GallerySaver(app),
                        instagramAvailable = ShareLauncher(app).isInstagramAvailable(),
                    )
                }
            }
        }
    }
}
